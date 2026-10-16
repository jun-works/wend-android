package app.wend.sea.feature.trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.wend.sea.core.designsystem.component.RenameDialog
import app.wend.sea.core.designsystem.component.SameNameDialog
import app.wend.sea.core.designsystem.component.renameGlow
import app.wend.sea.feature.analysis.autoName
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.component.key
import app.wend.sea.core.designsystem.component.rememberThumbnail
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.feature.analysis.formatCount
import app.wend.sea.feature.analysis.name
import app.wend.sea.feature.analysis.period
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

private val MapHeight = 220.dp
private fun serif(size: Int, line: Double) = TextStyle(
    fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * line).sp, lineHeightStyle = CssLineHeight,
)
private val TitleStyle = serif(26, 1.25)
private val DayStyle = serif(19, 1.2)
private val PlaceStyle = serif(17, 1.3)
private val TimeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

internal fun time(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(TimeFormat)
/** 장소에 머문 시간대 "오후 1:40 – 3:30"(1분 안이면 한 시각) */
internal fun whenText(start: Long, end: Long): String = if (end - start < 60_000) time(start) else "${time(start)} – ${time(end)}"
private fun PlaceUi.whenText(): String = whenText(start, end)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onPhotoClick: (place: Int, index: Int) -> Unit,
    /** `+N / 전체 보기` 타일 — [tile] 은 타일 자리(화면 좌표), 앨범이 그 자리에서 펼쳐진다 */
    onOpenPlace: (place: Int, tile: Rect) -> Unit,
    onBestShots: () -> Unit,
    /** 사진 정리하기 */
    onCleanUp: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trip = uiState.trip
    val places = uiState.places
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is TripDetailEvent.Renamed -> {
                    val result = snackbarHostState.showSnackbar(event.message, actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) event.undo()
                }
            }
        }
    }

    // 이름 바꾸기 창: 여행(-1) 또는 장소 순번
    var renaming by rememberSaveable { mutableStateOf<Int?>(null) }

    // 목록 항목 순번: 요약 0, 버튼 1, 그 뒤로 날짜 머리 + 장소들
    val placeItem = remember(uiState.days) {
        var index = 2
        buildMap {
            uiState.days.forEach { day ->
                index++ // 날짜 머리
                day.places.forEach { put(index++, it.index) }
            }
        }
    }
    // 핀이 설 곳: 지도 아래 60dp 선까지 올라온 마지막 장소. 그 섹션이 55% 지나가면 다음 장소로 걸어간다.
    val linePx = with(density) { 60.dp.toPx() }
    val target by remember(placeItem) {
        derivedStateOf {
            val layout = listState.layoutInfo
            // 마지막 장소들은 기준선까지 올라오지 못하므로, 목록 끝 한 화면 안에서는 기준선을 아래로 내려
            // 남은 장소를 차례로 지나 맨 끝에서 마지막 장소에 서게 한다
            val viewEnd = layout.viewportEndOffset.toFloat()
            val lastItem = layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 }
            val toEnd = lastItem?.let { (it.offset + it.size - viewEnd).coerceAtLeast(0f) }
            val endFactor = if (toEnd == null) 0f else (1f - toEnd / (viewEnd - linePx).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val line = linePx + (viewEnd - 1f - linePx) * endFactor
            var place = 0
            var frac = 0f
            layout.visibleItemsInfo.forEach { info ->
                val p = placeItem[info.index] ?: return@forEach
                if (info.offset <= line) {
                    place = p
                    frac = ((line - info.offset) / info.size).coerceIn(0f, 1f)
                }
            }
            if (listState.firstVisibleItemIndex > (placeItem.keys.maxOrNull() ?: 0)) place = places.lastIndex.coerceAtLeast(0)
            var walk = ((frac - .55f) / .45f).coerceIn(0f, 1f)
            walk = walk * walk * (3 - 2 * walk)
            // 마지막 장소에서는 더 걸어갈 곳이 없다
            if (place >= places.lastIndex) walk = 0f
            place to walk
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            WendTopAppBar(onBack = onBack, title = { Text(trip?.name.orEmpty()) })
            // 다 읽기 전에는 빈 지도·"0곳" 같은 빈 내용을 그리지 않는다
            if (trip == null) return@Column
            // 다 읽으면 지도와 내용이 부드럽게 나타난다(툭 바뀌지 않게)
            // 크게 보기·장소 사진에서 돌아올 때는 다시 흐려졌다 나타나지 않는다
            var faded by rememberSaveable { mutableStateOf(false) }
            val fadeIn = remember { Animatable(if (faded) 1f else 0f) }
            LaunchedEffect(Unit) {
                fadeIn.animateTo(1f, tween(260))
                faded = true
            }
            val contentAlpha = fadeIn.value
            // 지도는 위에 고정. 위치가 하나도 없는 여행(빠진 사진으로 만든 여행)은 지도를 숨긴다
            if (places.any { it.x != null && it.y != null }) TripMap(
                places = places,
                lonScale = uiState.lonScale,
                loadArea = viewModel::mapArea,
                target = { target },
                glow = uiState.glow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapHeight)
                    .graphicsLayer { alpha = contentAlpha }
                    .drawBehind {
                        drawLine(Color(0xFFD6CAB9), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
            )
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
                item(key = "sum") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                        EditableName(
                            name = trip?.name.orEmpty(),
                            style = TitleStyle,
                            editLabel = "여행 이름 바꾸기",
                            glow = uiState.glow.title,
                            glowToken = uiState.glow.token,
                            onEdit = { renaming = TRIP },
                        )
                        if (trip != null) {
                            Text(
                                "${trip.period} · ${formatCount(trip.mediaCount)}장 · ${places.count { it.sameAs == null }}곳",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                item(key = "actions") {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onBestShots()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { ActionLabel(Icons.Rounded.Star, "베스트샷 고르기") }
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onCleanUp()
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { ActionLabel(Icons.Outlined.AutoDelete, "사진 정리하기") }
                    }
                }
                uiState.days.forEach { day ->
                    stickyHeader(key = "day-${day.day}") { DayHeader(day) }
                    day.places.forEach { place ->
                        item(key = "place-${place.index}") {
                            PlaceSection(
                                place,
                                best = uiState.best,
                                glowToken = uiState.glow.token.takeIf { place.index in uiState.glow.places },
                                onRename = { renaming = place.index },
                                onPhotoClick = { index ->
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onPhotoClick(place.index, index)
                                },
                                onOpenAll = { tile ->
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onOpenPlace(place.index, tile)
                                },
                            )
                        }
                    }
                }
                places.lastOrNull()?.let { last ->
                    item(key = "end") {
                        Text(
                            "여행 끝 · ${last.name} ${time(last.end)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 56.dp, end = 20.dp, top = 18.dp).navigationBarsPadding(),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    when (val target = renaming) {
        null -> Unit
        TRIP -> trip?.let { t ->
            RenameDialog(
                title = "여행 이름 바꾸기",
                contextIcon = Icons.Outlined.Event,
                context = "${t.period} · ${formatCount(t.mediaCount)}장",
                label = "여행 이름",
                current = t.name,
                autoName = t.autoName,
                maxLength = 20,
                onDismiss = { renaming = null },
                onSave = { renaming = null; viewModel.renameTrip(it) },
            )
        }
        else -> places.getOrNull(target)?.let { p ->
            RenameDialog(
                title = "장소 이름 바꾸기",
                contextIcon = Icons.Outlined.Schedule,
                context = "${p.dayOrdinal}일째 · ${time(p.start)} · ${formatCount(p.count)}장",
                label = "장소 이름",
                current = p.name,
                autoName = p.autoName,
                maxLength = 20,
                onDismiss = { renaming = null },
                onSave = { renaming = null; viewModel.renamePlace(p.index, it) },
            )
        }
    }
    uiState.pending?.let { pending ->
        SameNameDialog(
            oldName = pending.oldName,
            where = pending.others.mapNotNull { i -> places.getOrNull(i)?.let { "${it.dayOrdinal}일째 · ${time(it.start)}" } },
            onDismiss = viewModel::cancelRename,
            onOnlyThis = { viewModel.confirmRename(all = false) },
            onAll = { viewModel.confirmRename(all = true) },
        )
    }
}

private const val TRIP = -1

/** 이름 + 옆의 ✎(시안 `.tp-rename`). 이름이 길면 말줄임, ✎는 항상 보인다 */
@Composable
private fun EditableName(name: String, style: TextStyle, editLabel: String, glow: Boolean, glowToken: Long, onEdit: () -> Unit, color: Color = Color.Unspecified) {
    val glowColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).renameGlow(glow, glowToken, glowColor),
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "$editLabel, 지금 이름 $name", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ActionLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
}

/** 시안 `.tp-day`: 지도 아래 고정되는 날짜 머리 — "1일째 · 5월 3일 일요일 · 장소 1 → 장소 2" */
@Composable
private fun DayHeader(day: DayUi) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${day.ordinal}일째", style = DayStyle, color = dayTextColor(day.day))
            val weekday = day.date.dayOfWeek.getDisplayName(DayTextStyle.FULL, Locale.KOREAN)
            Text(
                "${day.date.monthValue}월 ${day.date.dayOfMonth}일 $weekday · " + day.places.joinToString(" → ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

/** 시안 `.tp-stop`: 왼쪽 타임라인 + 번호, 이름, 시간대 · 장수, 사진 6칸(+n) */
@Composable
private fun PlaceSection(place: PlaceUi, best: Set<Long>, glowToken: Long?, onRename: () -> Unit, onPhotoClick: (index: Int) -> Unit, onOpenAll: (tile: Rect) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rail = colors.outlineVariant
    val surface = colors.surface
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(rail, topLeft = Offset(27.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) },
    ) {
        Box(
            Modifier
                .padding(start = 13.dp, top = 11.dp)
                .size(30.dp)
                .background(surface, CircleShape)
                .padding(3.dp)
                .background(dayTextColor(place.day), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${place.number}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 12.sp, lineHeightStyle = CssLineHeight),
                color = Color.White,
            )
        }
        Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 5.dp, bottom = 6.dp)) {
            // ✎ 누르는 영역(40dp)이 이름 줄(22dp)보다 커서 위아래로 9dp 씩 겹쳐 둔다 — 시안과 같은 간격
            EditableName(
                name = place.name,
                style = PlaceStyle,
                editLabel = "장소 이름 바꾸기",
                glow = glowToken != null,
                glowToken = glowToken ?: 0,
                onEdit = onRename,
                color = dayTextColor(place.day),
            )
            Text(
                "${place.whenText()} · ${formatCount(place.count)}장",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.offset(y = (-7).dp),
            )
            PhotoGrid(place.name, place.photos, count = place.count, best = best, onClick = onPhotoClick, onOpenAll = onOpenAll, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/**
 * 3열 정사각 사진. 7장 이상이면 앞 5장 + 6칸째는 "+나머지 / 전체 보기"(시안 ㉑ — 총 38장이면 +33),
 * 누르면 그 장소의 전체 사진으로 간다. 일반 사진은 크게 보기로 바로 간다.
 */
@Composable
private fun PhotoGrid(
    placeName: String,
    photos: List<MediaThumbnail>,
    count: Int,
    /** 사용자가 고른 베스트샷(사진 id) — 별 */
    best: Set<Long>,
    onClick: (index: Int) -> Unit,
    onOpenAll: (tile: Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMore = count > 6
    Column(modifier.clip(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        photos.chunked(3).forEachIndexed { row, rowPhotos ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 3).forEach { col ->
                    val index = row * 3 + col
                    val photo = rowPhotos.getOrNull(col)
                    val isMore = hasMore && index == 5
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                when {
                                    photo == null -> Modifier
                                    isMore -> Modifier
                                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .semantics { contentDescription = "$placeName 사진 ${formatCount(count)}장 전체 보기" }
                                        .clickable(role = Role.Button) { onOpenAll(bounds) }
                                    else -> Modifier
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .semantics {
                                            contentDescription = "$placeName 사진 ${index + 1}" + (if (photo.isBest(best)) ", 베스트샷" else "") + " 크게 보기"
                                        }
                                        .clickable(role = Role.Button) { onClick(index) }
                                },
                            ),
                    ) {
                        if (photo != null) {
                            AsyncImage(
                                model = rememberThumbnail(photo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (!isMore && photo.isBest(best)) BestStar(Modifier.padding(5.dp))
                            if (isMore) {
                                Column(
                                    Modifier.fillMaxSize().background(Color(0xA61F1A16)),
                                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("+${formatCount(count - 5)}", style = MoreCountStyle, color = Color.White)
                                    Text("전체 보기", style = MoreLabelStyle, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MediaThumbnail.isBest(best: Set<Long>) = !video && mediaId in best

/** 시안 `.tp-grid .star`: 사진 왼쪽 위 흰 원 + 노란 별 */
@Composable
internal fun BestStar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .shadow(1.5.dp, CircleShape)
            .background(Color(0xFFFFFDF9), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFD18A0C), modifier = Modifier.size(16.dp))
    }
}

private val MoreCountStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 20.sp, lineHeightStyle = CssLineHeight)
private val MoreLabelStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.4.sp, lineHeightStyle = CssLineHeight)
