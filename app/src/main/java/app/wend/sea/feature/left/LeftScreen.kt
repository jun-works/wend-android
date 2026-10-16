package app.wend.sea.feature.left

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.component.gooey
import app.wend.sea.core.designsystem.component.rememberReducedMotion
import app.wend.sea.core.designsystem.component.rememberThumbnail
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.data.LibraryItem
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.feature.analysis.photoCount
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 시안 ⑰ 빠진 사진: 날짜 머리(고정) + 4열 사진, 최신순. 사진을 누르면 크게 보기.
 * 선택은 앱 바의 선택 버튼이나 길게 누르기, 날짜마다 "모두 선택". 선택 중에는 앱 바가 "n장 선택됨" + ✕, 뒤로 가기는 선택부터 푼다.
 * 시그니처: 고른 사진들이 여행 색 방울로 줄어들며 한데 뭉쳐 사라진다 — 빠진 사진이 여행에 합류.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeftScreen(
    onBack: () -> Unit,
    /** 크게 보기 — [index] 는 화면 순서(최신순) */
    onOpen: (index: Int) -> Unit,
    viewModel: LeftViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    BackHandler(enabled = uiState.selecting) { viewModel.setSelecting(false) }

    // 방울이 날아갈 출발 자리(화면 기준)
    val tiles = remember { mutableStateMapOf<MediaThumbnail, Rect>() }
    val flying = uiState.flight?.media?.toSet().orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val width = constraints.maxWidth
        Column(Modifier.fillMaxSize()) {
            val count = uiState.selected.size
            WendTopAppBar(
                onBack = { if (uiState.selecting) viewModel.setSelecting(false) else onBack() },
                backIcon = if (uiState.selecting) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                backLabel = stringResource(if (uiState.selecting) R.string.left_end_selection else R.string.action_back),
                containerColor = if (uiState.selecting) colors.surfaceContainer else colors.surface,
                title = {
                    if (uiState.selecting) {
                        Text(if (count > 0) pluralStringResource(R.plurals.left_selected, count, count) else stringResource(R.string.pick_photos))
                    } else {
                        Column {
                            Text(stringResource(R.string.left_title))
                            if (uiState.loaded) {
                                Text(
                                    stringResource(R.string.left_subtitle, photoCount(uiState.total)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!uiState.selecting && uiState.total > 0) {
                        IconButton(onClick = { viewModel.setSelecting(true) }) {
                            Icon(Icons.Outlined.Checklist, contentDescription = stringResource(R.string.left_select), tint = colors.onSurfaceVariant)
                        }
                    }
                },
            )
            if (!uiState.loaded) return@Column
            if (uiState.total == 0) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.left_empty_title), style = DayStyle, color = colors.onSurface)
                    Text(
                        stringResource(R.string.left_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                return@Column
            }
            val indexOf = remember(uiState.days) { uiState.items.withIndex().associate { (i, it) -> it.media to i } }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.left_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                    )
                }
                uiState.days.forEach { day ->
                    stickyHeader(key = "day-${day.key}") {
                        DayHeader(
                            day = day,
                            selecting = uiState.selecting,
                            onSelectDay = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.toggleDay(day.key)
                            },
                        )
                    }
                    items(day.items.chunked(4), key = { "row-${it.first().media.video}-${it.first().media.mediaId}" }) { row ->
                        Row(
                            Modifier.padding(start = 3.dp, end = 3.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            row.forEach { item ->
                                LeftTile(
                                    item = item,
                                    label = stringResource(R.string.left_tile_label, day.label),
                                    selecting = uiState.selecting,
                                    selected = item.media in uiState.selected,
                                    hidden = item.media in flying,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { tiles[item.media] = it.boundsInRoot() },
                                    onClick = {
                                        if (uiState.selecting) {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            viewModel.toggle(item.media)
                                        } else {
                                            onOpen(indexOf[item.media] ?: 0)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggle(item.media)
                                    },
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item(key = "gap-${day.key}") { Spacer(Modifier.padding(bottom = 7.dp)) }
                }
            }
            if (uiState.selecting) {
                Row(
                    Modifier.background(colors.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = viewModel::askAdd,
                        enabled = count > 0 && uiState.flight == null,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { ButtonLabel(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_trip)) }
                    Button(
                        onClick = viewModel::askNew,
                        enabled = count > 0 && uiState.flight == null,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { ButtonLabel(Icons.Outlined.AddLocationAlt, stringResource(R.string.make_new_trip)) }
                }
            }
        }

        uiState.flight?.let { flight ->
            var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
                FlyIntoTrip(
                    starts = flight.media.mapNotNull { m -> tiles[m]?.let { m to it.translate(-origin) } },
                    color = flight.color,
                    targetX = width / 2f,
                    onDone = viewModel::onFlightDone,
                )
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (uiState.selecting) 80.dp else 0.dp))
    }

    uiState.choices?.let { choices ->
        AddToTripSheet(count = uiState.selected.size, choices = choices, onPick = viewModel::addTo, onDismiss = viewModel::dismissPickers)
    }
    uiState.newTrip?.let { nt ->
        NewTripDialog(context = nt.context, suggested = nt.suggested, onMake = viewModel::makeTrip, onDismiss = viewModel::dismissPickers)
    }
}

/** 시안 `.lf-day`: 고정 날짜 머리 — "6월 20일 토요일" · "2026 · 7장" · (선택 중) 모두 선택 */
@Composable
private fun DayHeader(day: LeftDayUi, selecting: Boolean, onSelectDay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .heightIn(min = 48.dp)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(day.label, style = DayStyle, color = colors.onSurface)
        Text(
            (day.date?.let { "${it.year} · " } ?: "") + photoCount(day.items.size),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selecting) {
            TextButton(onClick = onSelectDay) { Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** 시안 `.lf-grid button`: 정사각, 고르면 0.88 로 줄며 모서리가 둥글어지고 체크 원이 채워진다 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeftTile(
    item: LibraryItem,
    label: String,
    selecting: Boolean,
    selected: Boolean,
    hidden: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 0.88f else 1f, tween(200), label = "scale")
    val corner by animateDpAsState(if (selected) 10.dp else 0.dp, tween(200), label = "corner")
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (hidden) 0f else 1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(colors.surfaceContainer)
            .semantics {
                contentDescription = label
                if (selecting) this.selected = selected
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = rememberThumbnail(item.media),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.media.video) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp),
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (selected) colors.primary else CheckIdle, CircleShape)
                    .border(2.dp, if (selected) colors.primary else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * 시그니처: 고른 사진들이 차례로(45ms 간격) 화면 위쪽 한 점으로 날아가며 여행 색 원으로 줄고, 서로 붙어(goo) 한 방울이 된 뒤 걷힌다.
 * 시안 `.fly-layer`: 0.75초 cubic-bezier(.6,0,.3,1), 색은 0.15초 뒤 0.5초 동안. 움직임 줄이기면 바로 끝낸다.
 */
@Composable
private fun FlyIntoTrip(starts: List<Pair<MediaThumbnail, Rect>>, color: Color, targetX: Float, onDone: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val currentOnDone by rememberUpdatedState(onDone)
    val density = LocalDensity.current
    val targetY = with(density) { 120.dp.toPx() }
    val targetSize = with(density) { 34.dp.toPx() }
    val progress = remember(starts.size) { List(starts.size) { Animatable(0f) } }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion && starts.isNotEmpty()) {
            coroutineScope {
                progress.mapIndexed { i, p ->
                    async {
                        delay(i * 45L)
                        p.animateTo(1f, tween(750, easing = FlyEasing))
                    }
                }.awaitAll()
            }
            delay(150)
            fade.animateTo(0f, tween(300))
        }
        currentOnDone()
    }
    Box(Modifier.fillMaxSize().alpha(fade.value).gooey(blurSigma = 8.dp, alphaScale = 22f, alphaOffset = -9f, keepColors = true)) {
        starts.forEachIndexed { i, (media, rect) ->
            val t = progress[i].value
            val size = lerp(rect.width, targetSize, t)
            val cx = lerp(rect.center.x, targetX, t)
            val cy = lerp(rect.center.y, targetY, t)
            // 색은 움직임의 20% 뒤부터 물든다(시안 background-color .5s .15s)
            val tint = ((t - 0.2f) / 0.67f).coerceIn(0f, 1f)
            val sizeDp = with(density) { size.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((cx - size / 2).roundToInt(), (cy - size / 2).roundToInt()) }
                    .size(sizeDp)
                    .clip(RoundedCornerShape(lerp(6.dp, sizeDp / 2, t)))
                    .background(color),
            ) {
                AsyncImage(
                    model = rememberThumbnail(media),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(1f - tint),
                )
            }
        }
    }
}

@Composable
private fun ButtonLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
}

private val DayStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.8.sp, lineHeightStyle = CssLineHeight)

/** 시안 cubic-bezier(.6,0,.3,1) */
private val FlyEasing = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)

/** 시안 `rgba(0,0,0,.18)` */
private val CheckIdle = Color(0x2E000000)
