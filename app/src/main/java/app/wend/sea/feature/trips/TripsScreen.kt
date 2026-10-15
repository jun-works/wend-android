package app.wend.sea.feature.trips

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.TripGlobe
import app.wend.sea.core.designsystem.component.paperCard
import app.wend.sea.core.designsystem.component.tripColor
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.Trip
import app.wend.sea.feature.analysis.formatCount
import app.wend.sea.feature.analysis.name
import app.wend.sea.feature.analysis.period
import app.wend.sea.feature.analysis.tripDots
import app.wend.sea.feature.permission.rememberMediaPermissionRequest
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val GlobeSize = 200.dp
private val DockSize = 40.dp
private val DockScroll = 170.dp // 이만큼 스크롤하면 지구본이 앱 바에 완전히 붙는다

private fun serif(size: Int, line: Double) = TextStyle(
    fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * line).sp, lineHeightStyle = CssLineHeight,
)
private val WordmarkStyle = serif(22, 1.35).copy(letterSpacing = (-.3).sp)
private val YearStyle = serif(22, 1.0)
private val CardNameStyle = serif(19, 1.2)
private val SumNumberStyle = serif(20, 1.0)
private val CardCountStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 15.sp, lineHeightStyle = CssLineHeight)

@Composable
fun TripsScreen(
    onReanalyze: () -> Unit,
    onTripClick: (number: Int) -> Unit,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnReanalyze by rememberUpdatedState(onReanalyze)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.checkAccess() },
    )

    LifecycleResumeEffect(viewModel) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                TripsEvent.Reanalyze -> currentOnReanalyze()
            }
        }
    }

    val trips = uiState.trips
    val dots = remember(trips) { tripDots(trips) }
    // 목록 항목 순서: 머리 · (일부 허용 안내) · 연도 · 카드… · (빠진 사진)
    val cardIndex = remember(uiState) {
        var index = 1 + if (uiState.partial) 1 else 0
        buildMap {
            uiState.years.forEach { (_, yearTrips) ->
                index++ // 연도
                yearTrips.forEach { put(it.number, index++) }
            }
        }
    }

    // 스크롤 0 → 1: 지구본이 머리에서 앱 바로 옮겨 간다
    val dockScrollPx = with(density) { DockScroll.toPx() }
    val docking by remember {
        derivedStateOf {
            val scrolled = if (listState.firstVisibleItemIndex > 0) dockScrollPx else listState.firstVisibleItemScrollOffset.toFloat()
            val f = (scrolled / dockScrollPx).coerceIn(0f, 1f)
            f * f * (3 - 2 * f)
        }
    }
    // 보고 있는 여행: 앱 바 아래 24dp 선보다 아래로 내려온 첫 카드. 맨 위에서는 최신 여행.
    val lineOffsetPx = with(density) { 24.dp.toPx() }
    val focusNumber by remember(cardIndex) {
        derivedStateOf {
            val newest = cardIndex.keys.maxOrNull()
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 * density.density) return@derivedStateOf newest
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { info -> cardIndex.containsValue(info.index) && info.offset + info.size > lineOffsetPx }
                ?.let { info -> cardIndex.entries.first { it.value == info.index }.key }
        }
    }
    val focusIndex by remember(trips) { derivedStateOf { trips.indexOfFirst { it.number == focusNumber } } }

    var slotPos by remember { mutableStateOf(Offset.Zero) }
    var dockPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 시안 #trips .top-app-bar: 워드마크 + (지구본 자리) + 설정
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wend", style = WordmarkStyle, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
                Box(Modifier.size(DockSize).onGloballyPositioned { dockPos = it.positionInRoot() })
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    viewModel.onSettingsClick()
                }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item(key = "hero") {
                    Hero(
                        tripCount = trips.size,
                        photoCount = uiState.includedCount,
                        orbitAlpha = { (1 - docking * 2.5f).coerceIn(0f, 1f) },
                        onSlotPositioned = { slotPos = it },
                    )
                }
                if (uiState.partial) {
                    item(key = "partial") {
                        PartialBanner(onAllowAll = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (permission.canShowDialog()) permission.request() else permission.openSettings()
                        })
                    }
                }
                if (uiState.loaded && trips.isEmpty()) {
                    item(key = "empty") { EmptyTrips() }
                }
                uiState.years.forEach { (year, yearTrips) ->
                    item(key = "year-$year") { YearHeader(year, yearTrips.size) }
                    yearTrips.forEach { trip ->
                        item(key = "trip-${trip.number}") {
                            TripCard(
                                trip = trip,
                                covers = uiState.covers[trip.number].orEmpty(),
                                focused = trip.number == focusNumber,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onTripClick(trip.number)
                                },
                            )
                        }
                    }
                }
                if (uiState.leftCount > 0) {
                    item(key = "left") {
                        LeftOutRow(uiState.leftCount, onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.onLeftClick()
                        })
                    }
                }
                item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }

        // 지구본은 목록 위에 떠서, 스크롤에 따라 머리 자리 ↔ 앱 바 자리를 오간다
        TripGlobe(
            trips = dots,
            partial = uiState.partial,
            progress = { 1f },
            size = GlobeSize,
            description = "여행마다 다른 색 덩어리로 뭉친 지구본. 덩어리를 누르면 그 여행으로 이동해요",
            focus = { focusIndex },
            onClick = { picked ->
                if (docking > .7f) {
                    scope.launch { listState.animateScrollToItem(0) } // 앱 바에 붙은 지구본: 맨 위로
                } else if (picked != null) {
                    val number = trips.getOrNull(picked)?.number ?: return@TripGlobe
                    cardIndex[number]?.let { index ->
                        scope.launch { listState.animateScrollToItem(index, -with(density) { 28.dp.roundToPx() }) }
                    }
                }
            },
            modifier = Modifier.graphicsLayer {
                val f = docking
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = slotPos.x + (dockPos.x - slotPos.x) * f
                translationY = slotPos.y + (dockPos.y - slotPos.y) * f
                val scale = 1f + (DockSize / GlobeSize - 1f) * f
                scaleX = scale
                scaleY = scale
            },
        )

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** 시안 `.tr-hero`: 지구본 자리(점선 궤도) + "여행 N개 · 사진 M장" + 안내 */
@Composable
private fun Hero(tripCount: Int, photoCount: Int, orbitAlpha: () -> Float, onSlotPositioned: (Offset) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(GlobeSize)
                .onGloballyPositioned { onSlotPositioned(it.positionInRoot()) }
                .graphicsLayer { alpha = orbitAlpha() },
        ) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawCircle(colors.outlineVariant, radius = size.minDimension / 2 - 6.dp.toPx(), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }
        val number = SpanStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.onSurface)
        Text(
            text = if (tripCount > 0) {
                buildAnnotatedString {
                    append("여행 ")
                    withStyle(number) { append("$tripCount") }
                    append("개 · 사진 ")
                    withStyle(number) { append(formatCount(photoCount)) }
                    append("장")
                }
            } else {
                buildAnnotatedString { append("아직 찾은 여행이 없어요") }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (tripCount > 0) {
            Text(
                "색 덩어리를 누르면 그 여행으로 가요",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant.copy(alpha = .85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 시안 `.tr-year`: 세리프 연도 + 여행 수 */
@Composable
private fun YearHeader(year: Int, count: Int) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("$year", style = YearStyle)
        Text("여행 ${count}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 시안 `.tc`: 사진 3장(큰 1 + 작은 2, 마지막에 "+나머지") · 색 방울 · 이름 · 기간 · 장수. 보고 있으면 여행 색 테두리 */
@Composable
private fun TripCard(trip: Trip, covers: List<MediaThumbnail>, focused: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = tripColor(trip.number)
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(if (focused) color else Color.Transparent, label = "cardBorder")
    val dotScale by animateFloatAsState(if (focused) 1.18f else 1f, spring(dampingRatio = .45f), label = "dotScale")
    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .paperCard(shape, colors.surfaceContainerLowest)
            .border(BorderStroke(2.dp, border), shape)
            .clip(shape)
            .clickable(onClickLabel = "${trip.name} 열기", onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Cover(covers.getOrNull(0), Modifier.weight(2f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Cover(covers.getOrNull(1), Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Cover(covers.getOrNull(2), Modifier.fillMaxSize())
                    val more = trip.mediaCount - 3
                    if (more > 0) {
                        Box(Modifier.fillMaxSize().background(Color(0x731F1A16)), contentAlignment = Alignment.Center) {
                            Text("+${formatCount(more)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.padding(horizontal = 2.dp).size(22.dp).graphicsLayer { scaleX = dotScale; scaleY = dotScale }) {
                Box(Modifier.size(22.dp).background(color, CircleShape))
                Box(Modifier.offset(x = 19.dp, y = (-5).dp).size(10.dp).background(color, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(trip.name, style = CardNameStyle)
                Text(trip.period, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
            Text("${formatCount(trip.mediaCount)}장", style = CardCountStyle)
        }
    }
}

@Composable
private fun Cover(thumbnail: MediaThumbnail?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (thumbnail != null) {
            AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** 시안 `.tr-part`: 일부만 볼 수 있을 때 */
@Composable
private fun PartialBanner(onAllowAll: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("고르신 사진에서 찾은 여행만 보여요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = onAllowAll,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("모두 허용", style = MaterialTheme.typography.labelLarge) }
    }
}

/** 시안 `.tr-empty` */
@Composable
private fun EmptyTrips() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("여행이 생기면 여기에 모여요", style = serif(19, 1.3), modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "위치 태그를 켜고 찍은 사진이 쌓이면 틱과 핀이 여행을 찾아 드릴게요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 시안 `.tr-left`: 여행에 담기지 않은 사진 — 이유는 보여 주지 않고 개수만 */
@Composable
private fun LeftOutRow(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = colors.outlineVariant,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            buildAnnotatedString {
                append("여행에 담기지 않은 사진·영상 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(formatCount(count)) }
                append("개")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

