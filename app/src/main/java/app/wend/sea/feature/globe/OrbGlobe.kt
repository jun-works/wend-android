package app.wend.sea.feature.globe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wend.sea.core.designsystem.component.gooey
import app.wend.sea.core.designsystem.component.rememberReducedMotion
import app.wend.sea.core.designsystem.theme.OrbColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

val GlobeSize = 360.dp

private const val DOT_COUNT = 210
private const val FRAME_INTERVAL_NS = 1_000_000_000L / 60 - 2_000_000L // vsync 오차 여유
private const val UNIT = 1f / 720f * 360f // 시안 캔버스(720px) 좌표 → dp
private const val RADIUS = 292f
private val Paper = floatArrayOf(246f, 241f, 233f)
private val Ink = floatArrayOf(58f, 50f, 43f)
private val Sun = floatArrayOf(242f, 179f, 61f)
private val TripColors = listOf(OrbColors.Coral, OrbColors.Sun, OrbColors.Sea, OrbColors.Mint, OrbColors.Coral, OrbColors.Sea)
    .map { floatArrayOf(it.red * 255f, it.green * 255f, it.blue * 255f) }

private fun norm(x: Float, y: Float, z: Float): FloatArray {
    val l = hypot(hypot(x, y), z)
    return floatArrayOf(x / l, y / l, z / l)
}

// 여섯 개의 "여행" 중심점
private val Centers = listOf(
    norm(.8f, .3f, .5f), norm(-.6f, .5f, .6f), norm(.1f, -.7f, .7f),
    norm(-.5f, -.4f, -.75f), norm(.6f, .55f, -.6f), norm(-.9f, .1f, -.1f),
)

private class Dot(val v: FloatArray, val cluster: Int, val phase: Float, val size: Float, val scatter: Float)

private class Satellite(var angle: Float, val speed: Float, val tilt: Float, val radius: Float, val rgb: FloatArray)

/** 지구본 시뮬레이션 상태. 프레임마다 [tick] 으로 진행시키고 [draw] 로 그린다. */
private class GlobeSim {
    val dots: List<Dot>
    val satellites = (0..3).map { k ->
        Satellite(angle = k * 1.6f, speed = .5f + k * .12f, tilt = -.6f + k * .4f, radius = 1.12f + (k % 2) * .08f, rgb = TripColors[k])
    }
    var step = GlobeStep.When
    var k = 0f
    var km = 0f
    var scatter = 0f
    var rot = 0f
    var t = 0f

    // 프레임마다 객체를 만들지 않도록 투영 결과를 미리 잡아 둔 배열에 쓴다
    private val count = DOT_COUNT + 4
    private val px = FloatArray(count)
    private val py = FloatArray(count)
    private val pz = FloatArray(count)
    private val pr = FloatArray(count)
    private val pc = LongArray(count)
    private val order = IntArray(count) { it }
    private val rgb = FloatArray(3)

    init {
        val random = Random(7)
        val golden = (PI * (3 - sqrt(5.0))).toFloat()
        dots = (0 until DOT_COUNT).map { i ->
            val y = 1 - 2 * (i + .5f) / DOT_COUNT
            val r = sqrt(1 - y * y)
            val th = golden * i
            val v = floatArrayOf(cos(th) * r, y, sin(th) * r)
            val cluster = Centers.indices.maxBy { c -> Centers[c][0] * v[0] + Centers[c][1] * v[1] + Centers[c][2] * v[2] }
            Dot(v, cluster, random.nextFloat() * 6.28f, .75f + random.nextFloat() * .5f, .4f + random.nextFloat())
        }
    }

    fun settle() {
        k = if (step != GlobeStep.When) 1f else 0f
        km = if (step == GlobeStep.Group) 1f else 0f
        scatter = 0f
    }

    fun tick(dt: Float) {
        t += dt
        rot += dt * if (step == GlobeStep.Group) .22f else .34f
        val ease = min(1f, dt * 2.2f)
        k += ((if (step != GlobeStep.When) 1f else 0f) - k) * ease
        km += ((if (step == GlobeStep.Group) 1f else 0f) - km) * ease
        scatter *= exp(ln(.12f) * dt)
        satellites.forEach { it.angle += dt * it.speed }
    }

    fun DrawScope.draw() {
        val unit = UNIT.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val cr = cos(rot)
        val sr = sin(rot)
        val ca = cos(.38f)
        val sa = sin(.38f)
        val pull = k * .42f + km * .42f
        val timeStep = step == GlobeStep.When
        val grow = if (step == GlobeStep.Group) 1.35f else 1f

        for (i in 0 until DOT_COUNT) {
            val p = dots[i]
            val c = Centers[p.cluster]
            var vx = p.v[0] + (c[0] - p.v[0]) * pull
            var vy = p.v[1] + (c[1] - p.v[1]) * pull
            var vz = p.v[2] + (c[2] - p.v[2]) * pull
            val l = sqrt(vx * vx + vy * vy + vz * vz)
            vx /= l; vy /= l; vz /= l
            val rr = 1 + .035f * sin(t * 1.4f + p.phase) + scatter * p.scatter * .55f
            val x = vx * cr - vz * sr
            var z = vx * sr + vz * cr
            val y = vy * ca - z * sa
            z = vy * sa + z * ca
            val depth = (z + 1) / 2
            val wave = if (timeStep) max(0f, sin(t * 2.6f - p.v[1] * 4.5f)) else 0f
            // 1단계: 잉크색 점 위로 노란 "시간" 띠 · 2~3단계: 여행 색
            if (timeStep) {
                for (j in 0..2) rgb[j] = Ink[j] + (Sun[j] - Ink[j]) * wave
            } else {
                TripColors[p.cluster].copyInto(rgb)
            }
            px[i] = cx + x * RADIUS * rr * unit
            py[i] = cy + y * RADIUS * rr * unit
            pz[i] = z
            pr[i] = 11.5f * p.size * (.45f + .7f * depth) * (1 + wave * .35f) * grow * unit
            pc[i] = paperMix(rgb, ((1 - depth) * .62f).coerceIn(0f, .8f))
        }
        satellites.forEachIndexed { n, s ->
            val i = DOT_COUNT + n
            val x = cos(s.angle) * s.radius
            val z0 = sin(s.angle) * s.radius
            val z = z0 * cos(s.tilt)
            px[i] = cx + x * RADIUS * unit
            py[i] = cy + z0 * sin(s.tilt) * RADIUS * unit
            pz[i] = z
            pr[i] = (9 + 4 * (z + 1)) * unit
            pc[i] = paperMix(s.rgb, if (z < 0) .45f else 0f)
        }
        // 지난 프레임 순서가 거의 그대로라 삽입 정렬이면 대부분 O(n)
        for (a in 1 until count) {
            val idx = order[a]
            var b = a - 1
            while (b >= 0 && pz[order[b]] > pz[idx]) {
                order[b + 1] = order[b]
                b--
            }
            order[b + 1] = idx
        }
        for (i in order) drawCircle(Color(pc[i].toULong()), radius = pr[i], center = Offset(px[i], py[i]))
    }

    // 뒤쪽 점은 투명도 대신 종이색에 섞어 흐리게 — 알파 threshold 에 영향을 주지 않는다
    private fun paperMix(rgb: FloatArray, f: Float): Long = Color(
        red = (rgb[0] + (Paper[0] - rgb[0]) * f) / 255f,
        green = (rgb[1] + (Paper[1] - rgb[1]) * f) / 255f,
        blue = (rgb[2] + (Paper[2] - rgb[2]) * f) / 255f,
    ).value.toLong()
}

/**
 * Thinking orbs 지구본. 점 210개를 피보나치 구에 놓고 3D 회전시키며,
 * Gooey 효과로 가까운 점끼리 이어 붙인다. 탭하면 흩어졌다 다시 모인다.
 */
@Composable
fun OrbGlobe(step: GlobeStep, modifier: Modifier = Modifier) {
    val sim = remember { GlobeSim() }
    val haptic = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    var frame by remember { mutableLongStateOf(0L) }
    val currentStep by rememberUpdatedState(step)

    if (reducedMotion) {
        sim.step = step
        sim.settle()
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // 120Hz 화면에서도 60fps 로만 갱신 — 30fps 는 회전이 끊겨 보여서 60 으로 둔다
                if (last != 0L && now - last < FRAME_INTERVAL_NS) return@withFrameNanos
                val dt = if (last == 0L) .033f else min(.05f, (now - last) / 1e9f)
                last = now
                sim.step = currentStep
                sim.tick(dt)
                frame = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(GlobeSize)
            .gooey(blurSigma = 6.dp, keepColors = true)
            .semantics { contentDescription = "사진을 뜻하는 색 방울들이 지구본 모양으로 떠다니는 애니메이션" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "방울 흩뜨리기",
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                sim.scatter = 1.2f
            },
    ) {
        frame // 프레임마다 다시 그리도록 읽기
        with(sim) { draw() }
    }
}
