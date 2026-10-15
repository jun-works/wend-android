package app.wend.sea.feature.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.wend.sea.data.geo.MapArea
import app.wend.sea.data.geo.MapLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.core.designsystem.component.rememberReducedMotion
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.theme.OrbColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 날짜 색: 지도는 오브 색, 글자·번호는 대비 4.5:1 을 넘는 진한 색 */
private val DayMapColors = listOf(OrbColors.Coral, OrbColors.Sea, OrbColors.Mint)
private val DayTextColors = listOf(Color(0xFFB8391F), Color(0xFF1F63B3), Color(0xFF1D7A62))
fun dayMapColor(day: Int) = DayMapColors[day.mod(DayMapColors.size)]
fun dayTextColor(day: Int) = DayTextColors[day.mod(DayTextColors.size)]

// 종이 지도 색
private val Paper = Color(0xFFEFE8DD)
private val Sea = Color(0xFFDDE8E5)
private val RiverInk = Color(0xFFC3D6D8)
private val BorderInk = Color(0xFFB4A48D)
private val ProvinceInk = Color(0xFFD8CBB8)
private val RoadInk = Color(0xFFE0D2BC)
private val CityInk = Color(0xFF8A7D70)
private val CityStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, color = CityInk)

/** 하루 범위가 이보다 좁아도 위도 이만큼(약 9km)은 보여 준다 */
private const val MIN_SPAN_DEG = .08
private val Ring = Color(0xFFFFFDF9)
private const val INTRO_SECONDS = 1.6f
private const val FRAME_INTERVAL_NS = 1_000_000_000L / 60 - 2_000_000L
private val NumberStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 11.sp, lineHeightStyle = CssLineHeight, color = Color.White, textAlign = TextAlign.Center)

/** 지도 위 경로 한 구간(그날 위치 있는 장소 → 다음 위치 있는 장소) */
private class Segment(val path: Path, val length: Float, val measure: PathMeasure)

/** 그날 보이는 범위(지도 평면 좌표) */
private data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    fun lerp(to: Bounds, f: Double) = Bounds(
        minX + (to.minX - minX) * f, maxX + (to.maxX - maxX) * f,
        minY + (to.minY - minY) * f, maxY + (to.maxY - maxY) * f,
    )
    fun near(o: Bounds, eps: Double) =
        kotlin.math.abs(minX - o.minX) < eps && kotlin.math.abs(maxX - o.maxX) < eps &&
            kotlin.math.abs(minY - o.minY) < eps && kotlin.math.abs(maxY - o.maxY) < eps
}

/**
 * 종이 지도 — 지형 없이 장소 위치를 비율대로 찍고 경로만 그린다(인터넷을 쓰지 않음).
 * 지금 읽는 날짜 하루치만 보여 준다: 그날 들른 곳과 이동 경로, 그날 범위에 맞춘 확대.
 * 날짜가 바뀌면 지도가 그날로 옮겨 가고 그날 경로가 새로 그려진다. 핀은 [target] 까지 걸어가며 지나온 길을 진하게.
 *
 * @param target (장소 순번, 그 장소에서 다음 장소로 걸어간 정도 0–1)
 */
@Composable
fun TripMap(
    places: List<PlaceUi>,
    lonScale: Double,
    loadArea: suspend (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> MapArea,
    target: () -> Pair<Int, Float>,
    modifier: Modifier = Modifier,
    /** 방금 이름이 바뀐 장소들 — 지도 번호가 두 번 맥박친다 */
    glow: RenameGlow = RenameGlow(),
) {
    val reduced = rememberReducedMotion()
    val clock = rememberAmbientClock()
    val currentTarget by rememberUpdatedState(target)

    BoxWithConstraints(
        modifier
            .clipToBounds() // 기본 지도는 화면 범위보다 넓게 그리므로 지도 영역 밖은 잘라 낸다
            .background(Sea)
            .semantics { contentDescription = "그날 이동 경로 지도. 스크롤하면 핀이 경로를 따라 이동해요" },
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val pad = with(density) { 36.dp.toPx() }

        val day = places.getOrNull(currentTarget().first)?.day ?: 0
        // 그날 위치 있는 장소들. 같은 날 앞서 들른 곳과 300m 안이면 번호는 그곳 하나만(dayFirst).
        val located = remember(places, day) { places.filter { it.day == day && it.x != null } }
        val dayFirst = remember(located) {
            located.map { p ->
                located.firstOrNull { q -> q.index < p.index && q.sameAsKey() == p.sameAsKey() && p.sameAs != null } == null
            }
        }
        val targetBounds = remember(located) { boundsOf(located) }

        var bounds by remember { mutableStateOf(targetBounds) }
        var intro by remember { mutableFloatStateOf(if (reduced) 1f else 0f) }
        var walked by remember { mutableFloatStateOf(0f) }
        var shownDay by remember { mutableIntStateOf(day) }

        // 화면 좌표 · 경로는 범위가 바뀔 때만 다시 만든다
        val view = remember(bounds, w, h) { viewOf(bounds, w, h, pad, minSpan = MIN_SPAN_DEG) }
        val points = remember(located, view) { located.map { view.toScreen(it.x!!, it.y!!) } }
        val segments = remember(points) {
            (0 until max(0, points.size - 1)).map { i ->
                val a = points[i]
                val b = points[i + 1]
                val bend = if (a == b) 0f else .18f
                val mid = (a + b) / 2f
                val control = Offset(mid.x - (b.y - a.y) * bend, mid.y + (b.x - a.x) * bend)
                val path = Path().apply {
                    moveTo(a.x, a.y)
                    quadraticTo(control.x, control.y, b.x, b.y)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                Segment(path, measure.length, measure)
            }
        }
        val cumulative = remember(segments) { segments.runningFold(0f) { acc, s -> acc + s.length } }
        val total = cumulative.lastOrNull() ?: 0f
        // 루프는 한 번만 시작하므로 그날 장소·경로는 최신 값을 읽는다
        val latestLocated by rememberUpdatedState(located)
        val latestDay by rememberUpdatedState(day)
        val latestPlaces by rememberUpdatedState(places)
        val latestSegments by rememberUpdatedState(segments)
        val latestCumulative by rememberUpdatedState(cumulative)
        // 움직일 게 있을 때만 프레임을 돌리고, 다 그려지면 스크롤·날짜가 바뀔 때까지 쉰다(발열)
        LaunchedEffect(reduced) {
            while (true) {
                var last = 0L
                var settled = false
                while (!settled) {
                    withFrameNanos { now ->
                        if (last != 0L && now - last < FRAME_INTERVAL_NS) return@withFrameNanos
                        val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f)
                        last = now
                        val goal = boundsOf(latestLocated)
                        if (shownDay != latestDay) {
                            // 다른 날로 넘어감: 경로를 새로 그리고 핀은 그날 첫 자리에서
                            shownDay = latestDay
                            intro = if (reduced) 1f else 0f
                            walked = 0f
                        }
                        val span = max(goal.maxX - goal.minX, goal.maxY - goal.minY).coerceAtLeast(MIN_SPAN_DEG)
                        if (bounds != goal) {
                            // 처음엔 바로 맞추고, 날짜가 바뀔 때만 옮겨 가는 모습을 보인다. 거의 다 오면 딱 맞춘다.
                            val empty = bounds.minX == bounds.maxX && bounds.minY == bounds.maxY && bounds.minX == 0.0
                            bounds = if (reduced || empty || bounds.near(goal, span * .002)) goal else bounds.lerp(goal, min(1.0, dt * 6.0))
                        }
                        if (intro < 1f) intro = min(1f, intro + dt / INTRO_SECONDS)
                        val want = walkTarget(currentTarget(), latestPlaces, latestLocated, latestDay, latestSegments, latestCumulative)
                        walked = if (reduced || kotlin.math.abs(want - walked) < .5f) want else walked + (want - walked) * min(1f, dt * 6)
                        settled = bounds == goal && intro >= 1f && walked == want
                    }
                }
                val seen = Triple(currentTarget(), latestDay, latestLocated)
                snapshotFlow { Triple(currentTarget(), latestDay, latestLocated) }.first { it != seen }
            }
        }

        // 기본 지도: 그날 범위보다 넉넉하게 불러 둔다. 부동소수 정밀도를 위해 여행 첫 장소 기준 상대 좌표로 path 를 만든다.
        val origin = remember(places) { places.firstOrNull { it.x != null }?.let { it.x!! to it.y!! } ?: (0.0 to 0.0) }
        var area by remember { mutableStateOf<MapArea?>(null) }
        LaunchedEffect(targetBounds, lonScale) {
            if (located.isEmpty() || lonScale <= 0.0) return@LaunchedEffect
            val minLat = -targetBounds.maxY
            val maxLat = -targetBounds.minY
            val minLon = targetBounds.minX / lonScale
            val maxLon = targetBounds.maxX / lonScale
            val margin = max(.4, max(maxLat - minLat, maxLon - minLon))
            area = loadArea(minLat - margin, maxLat + margin, minLon - margin, maxLon + margin)
        }
        val basePaths = remember(area, origin, lonScale) { area?.let { buildBasePaths(it, origin, lonScale) } }
        val cityLabels = remember(area, view, points) { area?.let { pickCities(it, view, lonScale, w, h, density.density, points) }.orEmpty() }
        val textMeasurer = rememberTextMeasurer()

        val color = dayMapColor(day)
        // 기본 지도는 자기 레이어에 캐시 — 핀이 숨쉴 때마다 도로·경계를 다시 그리지 않게
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            basePaths?.let { drawBaseMap(it, view, origin) }
            cityLabels.forEach { (pos, name) ->
                drawCircle(CityInk, radius = 2.5.dp.toPx(), center = pos)
                drawText(textMeasurer, name, topLeft = pos + Offset(5.dp.toPx(), -8.dp.toPx()), style = CityStyle)
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val faint = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
            val solid = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            segments.forEachIndexed { i, seg ->
                val shown = (intro * total - cumulative[i]).coerceIn(0f, seg.length)
                val drawn = (walked - cumulative[i]).coerceIn(0f, seg.length)
                if (shown > 0f) drawPath(partial(seg, shown), color, alpha = .3f, style = faint)
                if (drawn > 0f) drawPath(partial(seg, drawn), color, style = solid)
            }
        }

        located.forEachIndexed { i, place ->
            if (!dayFirst[i]) return@forEachIndexed
            val p = points.getOrNull(i) ?: return@forEachIndexed
            Box(
                Modifier
                    .offset { IntOffset((p.x - 13.dp.toPx()).roundToInt(), (p.y - 13.dp.toPx()).roundToInt()) }
                    .renamePulse(place.index in glow.places, glow.token, dayTextColor(place.day))
                    .size(26.dp)
                    .background(Ring, CircleShape)
                    .padding(2.dp)
                    .background(dayTextColor(place.day), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${place.number}", style = NumberStyle) }
        }

        // 핀(장소 담당)이 지금 읽는 장소에 서 있다
        if (points.isNotEmpty()) {
            BotAvatar(
                Bot.Pin,
                clock,
                Modifier
                    .size(32.dp)
                    .graphicsLayer() // 숨쉬는 핀만 따로 다시 그린다
                    .offset {
                        val pos = positionAt(walked, segments, cumulative, points)
                        IntOffset((pos.x - 16.dp.toPx()).roundToInt(), (pos.y - 36.dp.toPx()).roundToInt())
                    },
            )
        }

        // 지금 보는 날짜
        val ordinal = places.firstOrNull { it.day == day }?.dayOrdinal ?: 1
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 10.dp)
                .background(Color(0xD9FBF8F3), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                "${ordinal}일째 · ${located.size}곳",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 같은 곳 표시용 키 — 앞서 들른 장소 순번(없으면 자기 자신) */
private fun PlaceUi.sameAsKey(): Int = sameAs ?: index

private fun boundsOf(located: List<PlaceUi>): Bounds {
    if (located.isEmpty()) return Bounds(0.0, 0.0, 0.0, 0.0)
    return Bounds(located.minOf { it.x!! }, located.maxOf { it.x!! }, located.minOf { it.y!! }, located.maxOf { it.y!! })
}

/** 평면 좌표 → 화면 좌표 변환(그날 범위를 가로세로 비율 유지로 맞춤) */
private class MapView(val scale: Double, val cx: Double, val cy: Double, val w: Float, val h: Float) {
    fun toScreen(x: Double, y: Double) = Offset((w / 2 + (x - cx) * scale).toFloat(), (h / 2 + (y - cy) * scale).toFloat())
}

/** 너무 좁은 하루(한 동네)도 어디쯤인지 보이도록 최소 [minSpan] 도 범위는 보여 준다 */
private fun viewOf(b: Bounds, w: Float, h: Float, pad: Float, minSpan: Double): MapView {
    val rangeX = max(b.maxX - b.minX, minSpan * w / h)
    val rangeY = max(b.maxY - b.minY, minSpan)
    val scale = min((w - 2 * pad) / rangeX, (h - 2 * pad) / rangeY)
    return MapView(scale, (b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, w, h)
}

/** 레이어별 path(여행 첫 장소 기준 평면 좌표) */
private class BasePaths(val fills: List<Pair<Path, Color>>, val lines: List<Triple<Path, Color, MapLayer>>)

private fun buildBasePaths(area: MapArea, origin: Pair<Double, Double>, lonScale: Double): BasePaths {
    fun ringPath(path: Path, ring: DoubleArray) {
        for (i in 0 until ring.size / 2) {
            val x = (ring[i * 2] * lonScale - origin.first).toFloat()
            val y = (-ring[i * 2 + 1] - origin.second).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    val fills = mutableListOf<Pair<Path, Color>>()
    val lines = mutableListOf<Triple<Path, Color, MapLayer>>()
    area.shapes.forEach { (layer, shapes) ->
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        shapes.forEach { shape ->
            shape.rings.forEach { ring ->
                ringPath(path, ring)
                if (layer.polygon) path.close()
            }
        }
        when (layer) {
            MapLayer.Land -> fills += path to Paper
            MapLayer.Lake -> fills += path to Sea
            MapLayer.River -> lines += Triple(path, RiverInk, layer)
            MapLayer.Border -> lines += Triple(path, BorderInk, layer)
            MapLayer.Province -> lines += Triple(path, ProvinceInk, layer)
            MapLayer.Road -> lines += Triple(path, RoadInk, layer)
        }
    }
    return BasePaths(fills, lines)
}

private fun DrawScope.drawBaseMap(paths: BasePaths, view: MapView, origin: Pair<Double, Double>) {
    val s = view.scale.toFloat()
    withTransform({
        translate(
            (view.w / 2 + (origin.first - view.cx) * view.scale).toFloat(),
            (view.h / 2 + (origin.second - view.cy) * view.scale).toFloat(),
        )
        scale(s, s, pivot = Offset.Zero)
    }) {
        paths.fills.forEach { (path, color) -> drawPath(path, color) }
        paths.lines.forEach { (path, color, layer) ->
            val width = when (layer) {
                MapLayer.Road -> 1.6.dp.toPx()
                MapLayer.Border -> 1.2.dp.toPx()
                MapLayer.River -> 1.dp.toPx()
                else -> .8.dp.toPx()
            } / s
            val dash = when (layer) {
                MapLayer.Border -> PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx() / s, 3.dp.toPx() / s))
                MapLayer.Province -> PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx() / s, 3.dp.toPx() / s))
                else -> null
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = dash))
        }
    }
}

/** 화면 안의 큰 도시부터, 서로 겹치지 않게 몇 개만 */
private fun pickCities(
    area: MapArea,
    view: MapView,
    lonScale: Double,
    w: Float,
    h: Float,
    density: Float,
    markers: List<Offset>,
): List<Pair<Offset, String>> {
    val picked = mutableListOf<Pair<Offset, String>>()
    val gap = 72 * density
    for (city in area.cities.sortedBy { it.rank }) {
        val p = view.toScreen(city.longitude * lonScale, -city.latitude)
        if (p.x < 8 * density || p.x > w - 60 * density || p.y < 12 * density || p.y > h - 24 * density) continue
        if (picked.any { (q, _) -> (q - p).getDistance() < gap }) continue
        // 장소 번호와 겹치지 않게(이름은 점 오른쪽으로 뻗는다)
        if (markers.any { m -> m.y in (p.y - 22 * density)..(p.y + 22 * density) && m.x in (p.x - 20 * density)..(p.x + 90 * density) }) continue
        picked += p to city.name
        if (picked.size == 5) break
    }
    return picked
}

/** 핀이 서야 할 경로 위 거리: 읽는 장소까지, 그 장소에서 다음 장소로 걸어간 만큼 더 */
private fun walkTarget(
    target: Pair<Int, Float>,
    places: List<PlaceUi>,
    located: List<PlaceUi>,
    day: Int,
    segments: List<Segment>,
    cumulative: List<Float>,
): Float {
    val (placeIndex, walk) = target
    val v = located.indexOfLast { it.index <= placeIndex }
    if (v < 0) return 0f
    val seg = segments.getOrNull(v) ?: return cumulative.lastOrNull() ?: 0f
    if (!(seg.length > 0f)) return cumulative[v]
    val nextIsLocatedSameDay = places.getOrNull(placeIndex + 1)?.let { it.x != null && it.day == day } == true
    return cumulative[v] + seg.length * (if (nextIsLocatedSameDay) walk else 0f)
}

private fun partial(seg: Segment, length: Float): Path =
    if (length >= seg.length) seg.path else Path().also { seg.measure.getSegment(0f, length, it, true) }

private fun positionAt(walked: Float, segments: List<Segment>, cumulative: List<Float>, points: List<Offset>): Offset {
    if (segments.isEmpty()) return points.first()
    var i = 0
    while (i < segments.lastIndex && walked > cumulative[i + 1]) i++
    val seg = segments[i]
    // 같은 자리를 연달아 찍으면 길이 0인 구간이 생긴다 — 그때는 꼭짓점에 그대로
    if (!(seg.length > 0f) || walked.isNaN()) return points[i]
    val pos = seg.measure.getPosition((walked - cumulative[i]).coerceIn(0f, seg.length))
    return if (pos.x.isNaN() || pos.y.isNaN()) points[i] else pos
}

/** 시안 `.tp-nums span.renamed`: 번호 둘레로 퍼지는 고리가 두 번 */
@Composable
private fun Modifier.renamePulse(active: Boolean, token: Long, color: Color): Modifier {
    val t = remember { Animatable(1f) }
    LaunchedEffect(token) {
        if (active && token > 0) repeat(2) {
            t.snapTo(0f)
            t.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
        }
    }
    return drawBehind {
        val v = t.value
        if (v < 1f) drawCircle(color.copy(alpha = 1f - v), radius = size.minDimension / 2 + 8.dp.toPx() * v, style = Stroke(2.dp.toPx()))
    }
}
