package app.wend.sea.feature.trip

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
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

private fun time(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(TimeFormat)
private fun PlaceUi.whenText(): String = if (end - start < 60_000) time(start) else "${time(start)} – ${time(end)}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
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
            }
        }
    }

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
            var place = 0
            var frac = 0f
            listState.layoutInfo.visibleItemsInfo.forEach { info ->
                val p = placeItem[info.index] ?: return@forEach
                if (info.offset <= linePx) {
                    place = p
                    frac = ((linePx - info.offset) / info.size).coerceIn(0f, 1f)
                }
            }
            if (listState.firstVisibleItemIndex > (placeItem.keys.maxOrNull() ?: 0)) place = places.lastIndex.coerceAtLeast(0)
            var walk = ((frac - .55f) / .45f).coerceIn(0f, 1f)
            walk = walk * walk * (3 - 2 * walk)
            place to walk
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            WendTopAppBar(onBack = onBack, title = { Text(trip?.name.orEmpty()) })
            // 지도는 위에 고정
            TripMap(
                places = places,
                lonScale = uiState.lonScale,
                loadArea = viewModel::mapArea,
                target = { target },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapHeight)
                    .drawBehind {
                        drawLine(Color(0xFFD6CAB9), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
            )
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "sum") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                        Text(trip?.name.orEmpty(), style = TitleStyle)
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
                                viewModel.onBestShots()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { ActionLabel(Icons.Rounded.Star, "베스트샷 고르기") }
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.onCleanUp()
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
                            PlaceSection(place, onPhotoClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.onPhotoClick()
                            })
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
private fun PlaceSection(place: PlaceUi, onPhotoClick: () -> Unit) {
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
        Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Text(place.name, style = PlaceStyle)
            Text(
                "${place.whenText()} · ${formatCount(place.count)}장",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            PhotoGrid(place.photos, more = place.count - 6, onClick = onPhotoClick, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** 3열 정사각 사진. 6칸째에 "+나머지" */
@Composable
private fun PhotoGrid(photos: List<MediaThumbnail>, more: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        photos.chunked(3).forEachIndexed { row, rowPhotos ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 3).forEach { col ->
                    val photo = rowPhotos.getOrNull(col)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(if (photo != null) Modifier.background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick) else Modifier),
                    ) {
                        if (photo != null) {
                            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (row == 1 && col == 2 && more > 0) {
                                Box(Modifier.fillMaxSize().background(Color(0x731F1A16)), contentAlignment = Alignment.Center) {
                                    Text("+${formatCount(more)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
