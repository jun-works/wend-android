package app.wend.sea.feature.viewer

import androidx.compose.ui.res.stringResource
import app.wend.sea.R
import app.wend.sea.core.text.AppText
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.animation.core.animate
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import app.wend.sea.feature.trip.StarGold
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.feature.trip.dayTextColor
import app.wend.sea.core.designsystem.component.cacheKey
import app.wend.sea.core.designsystem.component.key
import androidx.compose.foundation.layout.aspectRatio
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.theme.Surface
import app.wend.sea.feature.left.AddToTripSheet
import app.wend.sea.feature.left.NewTripDialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.size.Size as CoilSize
import java.time.Instant
import java.time.ZoneId

// 시안 #viewer: 어두운 바탕(#0f0d0b) 위 종이색 글자
private val ViewerBg = Color(0xFF0F0D0B)

/** 이만큼 끌어내리면(또는 빠르게 쓸어내리면) 크게 보기를 닫는다 */
private val DismissDistance = 120.dp
private const val DismissVelocity = 2000f
internal val ViewerInk = Color(0xFFF6F1E9)
private val ViewerSub = Color(0xFFC9BFB2)
internal val ViewerWarn = Color(0xFFFFB4A8)
private val TitleStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.5.sp, lineHeightStyle = CssLineHeight)

/**
 * 시안 ⑱ 사진 크게 보기(공통). 옆으로 넘겨 보고, 두 번 탭·두 손가락으로 확대한다.
 * 위: 날짜·시간 + 장소(그날 색 점), 아래: "n / N" + 들어온 곳에 따라
 * 베스트샷으로 · 여행에서 빼기 · 공유 · 휴지통(여행) / 여행에 넣기 · 새 여행 · 공유 · 휴지통(빠진 사진).
 */
@Composable
fun ViewerScreen(
    /** 연 화면의 크게 보기 — [ViewerViewModel.open] 으로 열고, 닫혀 있으면 아무것도 그리지 않는다 */
    viewModel: ViewerViewModel,
    /** 여행에서 뺐거나 휴지통으로 옮겨 여행이 바뀌었다 — 연 화면이 다시 읽는다. [message] 는 그 화면에서 띄울 안내 */
    onTripChanged: (message: String) -> Unit,
    /** 지금 보는 사진([MediaThumbnail.key]) — 닫으면 썸네일 화면이 그 사진을 보여 준다 */
    onPageChange: (media: Long) -> Unit = {},
) {
    val args by viewModel.args.collectAsStateWithLifecycle()
    if (args == null) return
    OpenViewer(viewModel, onBack = viewModel::close, onTripChanged = onTripChanged, onPageChange = onPageChange)
}

/** 연 화면 위에 겹쳐 그린다 — 열고 닫을 때 움직임 없이 바로. 끌어내리는 동안 아래 화면이 비친다 */
@Composable
private fun OpenViewer(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
    onTripChanged: (message: String) -> Unit,
    onPageChange: (media: Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnTripChanged by rememberUpdatedState(onTripChanged)

    DarkSystemBars()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ViewerEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ViewerEvent.BestChanged -> {
                    currentOnTripChanged("")
                    snackbarHostState.showSnackbar(event.message)
                }
                ViewerEvent.Removed -> {
                    currentOnTripChanged(AppText.string(R.string.viewer_removed))
                    currentOnBack()
                }
                is ViewerEvent.Moved -> {
                    currentOnTripChanged(event.message)
                    currentOnBack()
                }
            }
        }
    }

    // 여행에서 빼기: 사진이 그날 색 방울로 줄어들어 떨어져 나간다(0~1)
    val removing = remember { Animatable(0f) }
    var removingItem by remember { mutableStateOf<ViewerItem?>(null) }
    val scope = rememberCoroutineScope()
    // 아래로 끌어 닫기(0~1): 배경과 위아래 버튼이 옅어진다
    var dismiss by remember { mutableFloatStateOf(0f) }
    val chrome = Modifier.graphicsLayer { alpha = (1f - removing.value) * (1f - dismiss * 4f).coerceAtLeast(0f) }

    // 아래 화면으로 손이 닿지 않게 막는다
    Box(Modifier.fillMaxSize().pointerInput(Unit) {}.background(ViewerBg.copy(alpha = 1f - dismiss * .85f))) {
        if (!uiState.loaded) return@Box
        val items = uiState.items
        // 휴지통으로 모두 옮겨 볼 사진이 없으면 닫는다
        LaunchedEffect(items.isEmpty()) {
            if (items.isEmpty()) {
                currentOnTripChanged("")
                currentOnBack()
            }
        }
        if (items.isEmpty()) return@Box

        val pager = rememberPagerState(initialPage = uiState.startIndex) { items.size }
        var zoomed by remember { mutableStateOf(false) }
        val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
        val currentOnPageChange by rememberUpdatedState(onPageChange)
        LaunchedEffect(current) { currentOnPageChange(current.media.key) }

        var trashing by remember { mutableStateOf<ViewerItem?>(null) }
        val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val item = trashing
            trashing = null
            if (result.resultCode == Activity.RESULT_OK && item != null) {
                viewModel.onTrashed(item)
                currentOnTripChanged("")
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(chrome) { ViewerTopBar(current, uiState.placeName, uiState.day, onBack) }
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && removingItem == null && dismiss == 0f,
                beyondViewportPageCount = 1,
                key = { items[it].media.mediaId * 2 + if (items[it].media.video) 1 else 0 },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val item = items[page]
                ZoomableMedia(
                    item = item,
                    active = page == pager.currentPage,
                    removeProgress = if (removingItem == item) removing.value else 0f,
                    best = !item.media.video && item.media.mediaId in uiState.best,
                    dayColor = dayTextColor(uiState.day),
                    onZoomChange = { if (page == pager.currentPage) zoomed = it },
                    onDismissProgress = { dismiss = it },
                    onDismiss = onBack,
                    onPlay = {
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(items[page].media.uri, "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            Text(
                "${pager.currentPage + 1} / ${items.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE9E1D4),
                modifier = chrome
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 10.dp)
                    .background(ViewerInk.copy(alpha = .12f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Row(
                modifier = chrome.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                if (uiState.left) {
                    ViewerAction(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_trip)) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.askAdd(current)
                    }
                    ViewerAction(Icons.Outlined.AddLocationAlt, stringResource(R.string.new_trip)) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.askNew(current)
                    }
                } else {
                val isBest = !current.media.video && current.media.mediaId in uiState.best
                ViewerAction(
                    if (isBest) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    stringResource(if (isBest) R.string.best_shot else R.string.viewer_make_best),
                    iconTint = if (isBest) StarGold else ViewerInk,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    viewModel.onBestShot(current)
                }
                ViewerAction(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.viewer_remove_from_trip)) {
                    if (removingItem != null) return@ViewerAction
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    val item = current
                    removingItem = item
                    scope.launch {
                        removing.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
                        viewModel.onRemoveFromTrip(item)
                    }
                }
                }
                ViewerAction(Icons.Outlined.Share, stringResource(R.string.action_share)) {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType(if (current.media.video) "video/*" else "image/*")
                        .putExtra(Intent.EXTRA_STREAM, current.media.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, null))
                }
                // 휴지통은 시스템 확인창(MediaStore.createTrashRequest)으로만 — Wend 가 직접 지우지 않는다
                ViewerAction(Icons.Outlined.Delete, stringResource(R.string.viewer_trash), tint = ViewerWarn) {
                    val request = MediaStore.createTrashRequest(context.contentResolver, listOf(current.media.uri), true)
                    trashing = current
                    trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))

        val target = current
        uiState.choices?.let { choices ->
            AddToTripSheet(count = 1, choices = choices, onPick = { viewModel.addTo(target, it) }, onDismiss = viewModel::dismissPickers)
        }
        uiState.newTrip?.let { nt ->
            NewTripDialog(context = nt.context, suggested = nt.suggested, onMake = { viewModel.makeTrip(target, it) }, onDismiss = viewModel::dismissPickers)
        }
    }
}

/** 고르는 화면에서 크게 볼 사진 한 장: 위에 보일 장소와 그날 순번(색) */
data class PickPhoto(val item: ViewerItem, val place: String, val day: Int)

/**
 * 시안 ⑮·⑯ 크게 보기: 베스트샷 고르기·사진 정리하기 위에 열린다. 같은 묶음끼리 넘기고 확대하는 것은 크게 보기와 같고,
 * 아래 버튼만 그 화면의 고르기([action]) + 공유. 고른 상태는 연 화면의 ViewModel 이 가진다 — 돌아가면 목록에 그대로 보인다.
 */
@Composable
fun PickViewer(
    photos: List<PickPhoto>,
    startIndex: Int,
    /** 사진 왼쪽 위 "★ 베스트샷" */
    best: (ViewerItem) -> Boolean,
    onBack: () -> Unit,
    action: @Composable (ViewerItem) -> Unit,
) {
    val context = LocalContext.current
    DarkSystemBars()
    BackHandler(onBack = onBack)
    var dismiss by remember { mutableFloatStateOf(0f) }
    // 위아래 글자·버튼은 빨리 걷힌다 — 아래 목록 글자와 겹쳐 보이지 않게
    val chrome = Modifier.graphicsLayer { alpha = (1f - dismiss * 4f).coerceAtLeast(0f) }
    // 아래 화면으로 손이 닿지 않게 막는다
    Box(Modifier.fillMaxSize().pointerInput(Unit) {}.background(ViewerBg.copy(alpha = 1f - dismiss * .85f))) {
        if (photos.isEmpty()) return@Box
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
        var zoomed by remember { mutableStateOf(false) }
        val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
        Column(Modifier.fillMaxSize()) {
            Box(chrome) { ViewerTopBar(current.item, current.place, current.day, onBack) }
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && dismiss == 0f,
                beyondViewportPageCount = 1,
                key = { photos[it].item.media.mediaId * 2 + if (photos[it].item.media.video) 1 else 0 },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val photo = photos[page]
                ZoomableMedia(
                    item = photo.item,
                    active = page == pager.currentPage,
                    removeProgress = 0f,
                    best = best(photo.item),
                    dayColor = dayTextColor(photo.day),
                    onZoomChange = { if (page == pager.currentPage) zoomed = it },
                    onDismissProgress = { dismiss = it },
                    onDismiss = onBack,
                    onPlay = {
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(photo.item.media.uri, "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            Text(
                "${pager.currentPage + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE9E1D4),
                modifier = chrome
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 10.dp)
                    .background(ViewerInk.copy(alpha = .12f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Row(
                modifier = chrome.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                action(current.item)
                ViewerAction(Icons.Outlined.Share, stringResource(R.string.action_share)) {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType(if (current.item.media.video) "video/*" else "image/*")
                        .putExtra(Intent.EXTRA_STREAM, current.item.media.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, null))
                }
            }
        }
    }
}

/**
 * 어두운 화면 동안 시스템 바를 어둡게(밝은 글자), 나가면 종이색으로 되돌린다.
 * 테마의 statusBarColor(콜드 스타트용 종이색)를 그대로 그리는 기기가 있어 창의 바 색도 직접 바꾼다.
 */
@Suppress("DEPRECATION")
@Composable
private fun DarkSystemBars() {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity) {
        val bg = ViewerBg.toArgb()
        activity.enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(bg), navigationBarStyle = SystemBarStyle.dark(bg))
        activity.window.statusBarColor = bg
        activity.window.navigationBarColor = bg
        onDispose {
            val paper = Surface.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(paper, paper),
                navigationBarStyle = SystemBarStyle.light(paper, paper),
            )
            activity.window.statusBarColor = paper
            activity.window.navigationBarColor = paper
        }
    }
}

/** 시안 #viewer .top-app-bar: 뒤로 + "2026년 5월 3일 오후 1:40" / 그날 색 점 + 장소 */
@Composable
private fun ViewerTopBar(item: ViewerItem, placeName: String, day: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = ViewerInk)
        }
        Column(Modifier.padding(start = 4.dp, end = 12.dp)) {
            item.takenAt?.let {
                Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(AppText.dateFormat(R.string.pattern_viewer_datetime)), style = TitleStyle, color = ViewerInk)
            }
            // 빠진 사진에는 장소가 없다
            if (placeName.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(8.dp).background(dayTextColor(day), CircleShape))
                    Text(placeName, style = MaterialTheme.typography.bodySmall, color = ViewerSub, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** 시안 .vw-act: 아이콘 위 · 글자 아래, 최소 76×56 */
@Composable
internal fun ViewerAction(icon: ImageVector, label: String, tint: Color = ViewerInk, iconTint: Color = tint, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * 사진 한 장(영상은 첫 장면 + 재생 버튼). 두 번 탭하면 2.5배/원래대로, 두 손가락으로 1~5배.
 * 확대 중에는 옆으로 넘기지 않고 사진을 움직인다.
 */
@Composable
private fun ZoomableMedia(
    item: ViewerItem,
    active: Boolean,
    removeProgress: Float,
    /** 베스트샷이면 사진 왼쪽 위에 "★ 베스트샷" */
    best: Boolean,
    dayColor: Color,
    onZoomChange: (Boolean) -> Unit,
    /** 아래로 끌어 닫는 정도 0~1 — 화면이 배경·버튼을 옅게 한다 */
    onDismissProgress: (Float) -> Unit,
    /** 충분히 끌어내렸다 — 닫는다 */
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    // 아래로 끌어 닫기: 보고 있는 장만 손가락을 따라 내려간다(옆 장은 그대로)
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentOnDismissProgress by rememberUpdatedState(onDismissProgress)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissRange = with(LocalDensity.current) { DismissDistance.toPx() }
    LaunchedEffect(dragY) { if (active) currentOnDismissProgress((dragY / (dismissRange * 2.5f)).coerceIn(0f, 1f)) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    // 다른 장으로 넘어가면 원래 크기로
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(scale > 1f) { currentOnZoomChange(scale > 1f) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2
        val maxY = size.height * (s - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(item) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val s = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = s
                            offset = clamp((center - tap) * (s - 1), s)
                        }
                    },
                )
            }
            .pointerInput(item, active) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 한 손가락으로 아래로 끌기 시작하면 닫기, 옆으로면 넘기기(페이저)에 맡긴다
                    var dragging = false
                    var undecided = active && removeProgress == 0f
                    var moved = Offset.Zero
                    val tracker = VelocityTracker()
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (!dragging && (pinching || scale > 1f)) {
                            undecided = false
                            val s = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = clamp(offset + event.calculatePan(), s)
                            scale = s
                            if (s == 1f) offset = Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (dragging || undecided) {
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            if (!dragging) {
                                moved += delta
                                if (moved.getDistance() > slop) {
                                    undecided = false
                                    dragging = moved.y > 0 && abs(moved.y) > abs(moved.x) * 1.2f
                                }
                            }
                            if (dragging) {
                                dragY = (dragY + delta.y).coerceAtLeast(0f)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dragging) {
                        val velocity = tracker.calculateVelocity().y
                        if (dragY > dismissRange || velocity > DismissVelocity) {
                            currentOnDismiss()
                        } else {
                            scope.launch { animate(dragY, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> dragY = v } }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 빼기: 앞 70% 동안 사진이 둥글게 줄며 그날 색으로 물들고, 남은 30% 동안 방울이 아래로 떨어지며 사라진다
        val morph = (removeProgress / .7f).coerceIn(0f, 1f)
        val fall = ((removeProgress - .7f) / .3f).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp)
                .graphicsLayer {
                    // 끌어내리는 만큼 조금 작아진다
                    val s = (1f - morph * .82f) * (1f - (dragY / (dismissRange * 2.5f)).coerceIn(0f, 1f) * .2f)
                    scaleX = s
                    scaleY = s
                    translationY = fall * 220.dp.toPx() + dragY
                    alpha = 1f - fall
                    if (morph > 0f) {
                        shape = DropShape(morph)
                        clip = true
                    }
                },
        ) {
            // 원본 비율을 알면 사진 자리를 그 비율로 잡고 Crop — 썸네일(정사각 Crop)에서 날아올 때 자르는 범위가 자연스럽게 풀린다
            // 비율: 목록에서 읽어 둔 썸네일(원본 비율 그대로)에서 바로, 없으면 MediaStore 값
            val loader = SingletonImageLoader.get(LocalContext.current)
            val thumbRatio = remember(item) {
                loader.memoryCache?.get(MemoryCache.Key(item.media.cacheKey))?.image?.let { if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else null }
            }
            val ratio = (thumbRatio ?: item.ratio)?.takeIf { morph == 0f }
            val scaleMode = if (ratio != null || morph > 0f) ContentScale.Crop else ContentScale.Fit
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    (if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                ) {
                    val context = LocalContext.current
                    val request = remember(item) {
                        // 넘겨 보는 동안은 화면 크기로(빠르고 가볍게), 영상은 Android 가 만든 첫 장면.
                        // 읽는 동안은 목록에서 이미 읽은 썸네일을 먼저 보여 준다
                        ImageRequest.Builder(context)
                            .data(if (item.media.video) item.media else item.media.uri)
                            .placeholderMemoryCacheKey(item.media.cacheKey)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = stringResource(if (item.media.video) R.string.video else R.string.photo),
                        contentScale = scaleMode,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 확대하면 이 장만 원본 해상도로 불러와 위에 겹친다 — 확대해도 원본만큼 선명하게
                    if (!item.media.video && scale > 1f) {
                        val original = remember(item) {
                            ImageRequest.Builder(context).data(item.media.uri).size(CoilSize.ORIGINAL).build()
                        }
                        AsyncImage(model = original, contentDescription = null, contentScale = scaleMode, modifier = Modifier.fillMaxSize())
                    }
                    if (best && scale == 1f && morph == 0f) BestChip(Modifier.align(Alignment.TopStart).padding(12.dp))
                }
            }
            if (morph > 0f) Box(Modifier.fillMaxSize().background(dayColor.copy(alpha = morph)))
        }
        if (item.media.video && scale == 1f) {
            val playLabel = stringResource(R.string.viewer_play_video)
            Box(
                Modifier
                    .size(64.dp)
                    .background(Color(0x991F1A16), CircleShape)
                    .pointerInput(onPlay) { detectTapGestures(onTap = { onPlay() }) }
                    .semantics { contentDescription = playLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ViewerInk, modifier = Modifier.size(36.dp)) }
        }
    }
}

/** 시안 `.vw-slide .star`: 사진 왼쪽 위 "★ 베스트샷" 알약 */
@Composable
private fun BestChip(modifier: Modifier = Modifier) {
    val bestLabel = stringResource(R.string.best_shot)
    Row(
        modifier
            .background(Color(0xFFFFFDF9), CircleShape)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = bestLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFD18A0C), modifier = Modifier.size(16.dp))
        Text(bestLabel, style = BestChipStyle, color = Color(0xFF7A4F05))
    }
}

private val BestChipStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 12.sp, lineHeightStyle = CssLineHeight)

/** 빼기 때 사진을 자르는 원: 처음엔 사진 전체를 덮는 원, 끝엔 짧은 변에 맞는 원(세로 사진도 타원이 아닌 방울이 되게) */
private class DropShape(private val t: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = kotlin.math.hypot(size.width, size.height) / 2
        val small = minOf(size.width, size.height) / 2
        val r = full + (small - full) * t
        val c = Offset(size.width / 2, size.height / 2)
        return Outline.Generic(Path().apply { addOval(Rect(c, r)) })
    }
}
