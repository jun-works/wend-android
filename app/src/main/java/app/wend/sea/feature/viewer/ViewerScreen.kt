package app.wend.sea.feature.viewer

import android.app.Activity
import android.content.Intent
import android.os.Build
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
import androidx.compose.material.icons.outlined.Delete
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.feature.trip.dayTextColor
import app.wend.sea.core.designsystem.component.PhotoKey
import app.wend.sea.core.designsystem.component.cacheKey
import app.wend.sea.core.designsystem.component.key
import app.wend.sea.core.designsystem.component.sharedPhoto
import androidx.compose.foundation.layout.aspectRatio
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.theme.Surface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.size.Size as CoilSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 시안 #viewer: 어두운 바탕(#0f0d0b) 위 종이색 글자
private val ViewerBg = Color(0xFF0F0D0B)
private val ViewerInk = Color(0xFFF6F1E9)
private val ViewerSub = Color(0xFFC9BFB2)
private val ViewerWarn = Color(0xFFFFB4A8)
private val TitleStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.5.sp, lineHeightStyle = CssLineHeight)
private val DateTimeFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN)

/**
 * 시안 ⑱ 사진 크게 보기(여행 상세에서 열림). 옆으로 넘겨 그 장소의 사진을 보고, 두 번 탭·두 손가락으로 확대한다.
 * 위: 날짜·시간 + 장소(그날 색 점), 아래: "n / N" + 베스트샷으로 · 여행에서 빼기 · 공유 · 휴지통.
 */
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    /** 여행에서 뺐거나 휴지통으로 옮겨 여행이 바뀌었다 — 상세가 다시 읽는다. [message] 는 상세에서 띄울 안내 */
    onTripChanged: (message: String) -> Unit,
    /** 지금 보는 사진([MediaThumbnail.key]) — 돌아가면 썸네일 화면이 그 사진을 보여 준다 */
    onPageChange: (media: Long) -> Unit = {},
    viewModel: ViewerViewModel = hiltViewModel(),
) {
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
                    currentOnTripChanged("여행에서 뺐어요. 빠진 사진에서 다시 넣을 수 있어요")
                    currentOnBack()
                }
            }
        }
    }

    // 여행에서 빼기: 사진이 그날 색 방울로 줄어들어 떨어져 나간다(0~1)
    val removing = remember { Animatable(0f) }
    var removingItem by remember { mutableStateOf<ViewerItem?>(null) }
    val scope = rememberCoroutineScope()
    val chrome = Modifier.graphicsLayer { alpha = 1f - removing.value }

    Box(Modifier.fillMaxSize().background(ViewerBg)) {
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
                userScrollEnabled = !zoomed && removingItem == null,
                beyondViewportPageCount = 1,
                key = { items[it].media.mediaId * 2 + if (items[it].media.video) 1 else 0 },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val item = items[page]
                ZoomableMedia(
                    item = item,
                    active = page == pager.currentPage,
                    // 보고 있는 장만 썸네일과 같이 움직인다(옆 장이 다른 썸네일로 날아가지 않게)
                    sharedKey = if (page == pager.currentPage) PhotoKey(item.media.key, uiState.album) else null,
                    removeProgress = if (removingItem == item) removing.value else 0f,
                    best = !item.media.video && item.media.mediaId in uiState.best,
                    dayColor = dayTextColor(uiState.day),
                    onZoomChange = { if (page == pager.currentPage) zoomed = it },
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
                val isBest = !current.media.video && current.media.mediaId in uiState.best
                ViewerAction(
                    if (isBest) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    if (isBest) "베스트샷" else "베스트샷으로",
                    iconTint = if (isBest) StarGold else ViewerInk,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    viewModel.onBestShot(current)
                }
                ViewerAction(Icons.Outlined.RemoveCircleOutline, "여행에서 빼기") {
                    if (removingItem != null) return@ViewerAction
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    val item = current
                    removingItem = item
                    scope.launch {
                        removing.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
                        viewModel.onRemoveFromTrip(item)
                    }
                }
                ViewerAction(Icons.Outlined.Share, "공유") {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType(if (current.media.video) "video/*" else "image/*")
                        .putExtra(Intent.EXTRA_STREAM, current.media.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, null))
                }
                // 휴지통은 Android 11+ 시스템 확인창(MediaStore.createTrashRequest)으로만 — Wend 가 직접 지우지 않는다
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ViewerAction(Icons.Outlined.Delete, "휴지통", tint = ViewerWarn) {
                        val request = MediaStore.createTrashRequest(context.contentResolver, listOf(current.media.uri), true)
                        trashing = current
                        trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로", tint = ViewerInk)
        }
        Column(Modifier.padding(start = 4.dp, end = 12.dp)) {
            item.takenAt?.let {
                Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormat), style = TitleStyle, color = ViewerInk)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(8.dp).background(dayTextColor(day), CircleShape))
                Text(placeName, style = MaterialTheme.typography.bodySmall, color = ViewerSub, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** 시안 .vw-act: 아이콘 위 · 글자 아래, 최소 76×56 */
@Composable
private fun ViewerAction(icon: ImageVector, label: String, tint: Color = ViewerInk, iconTint: Color = tint, onClick: () -> Unit) {
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
    sharedKey: PhotoKey?,
    removeProgress: Float,
    /** 베스트샷이면 사진 왼쪽 위에 "★ 베스트샷" */
    best: Boolean,
    dayColor: Color,
    onZoomChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
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
            .pointerInput(item) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            val s = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = clamp(offset + event.calculatePan(), s)
                            scale = s
                            if (s == 1f) offset = Offset.Zero
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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
                    val s = 1f - morph * .82f
                    scaleX = s
                    scaleY = s
                    translationY = fall * 220.dp.toPx()
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
                        .sharedPhoto(sharedKey)
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
                        contentDescription = if (item.media.video) "영상" else "사진",
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
            Box(
                Modifier
                    .size(64.dp)
                    .background(Color(0x991F1A16), CircleShape)
                    .pointerInput(onPlay) { detectTapGestures(onTap = { onPlay() }) }
                    .semantics { contentDescription = "영상 재생" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ViewerInk, modifier = Modifier.size(36.dp)) }
        }
    }
}

/** 시안 `.vw-slide .star`: 사진 왼쪽 위 "★ 베스트샷" 알약 */
@Composable
private fun BestChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(0xFFFFFDF9), CircleShape)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = "베스트샷" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFD18A0C), modifier = Modifier.size(16.dp))
        Text("베스트샷", style = BestChipStyle, color = Color(0xFF7A4F05))
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
