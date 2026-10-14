package app.wend.sea.core.designsystem.component

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wend.sea.core.designsystem.theme.OrbColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val N = 280
private const val FRAME_INTERVAL_NS = 1_000_000_000L / 60 - 2_000_000L // vsync 오차 여유
private val Paper = floatArrayOf(246f, 241f, 233f)
private val Ghost = floatArrayOf(214f, 202f, 185f)
private val Ink = floatArrayOf(58f, 50f, 43f)
private val SunRgb = floatArrayOf(242f, 179f, 61f)
private val Dim = floatArrayOf(176f, 164f, 150f)

/** 여행 색 — 오브 4색을 찍은 순서대로 돌려 쓴다 */
val TripColors = listOf(OrbColors.Sun, OrbColors.Sea, OrbColors.Coral, OrbColors.Mint)

fun tripColor(number: Int): Color = TripColors[(number - 1).mod(TripColors.size)]

/** 지구본 위 여행 하나: 색과, 분석 진행(p)이 몇일 때 물들고 뭉치는지 */
data class TripDot(val color: Color, val at: Float)

private fun norm(x: Float, y: Float, z: Float): FloatArray {
    val l = hypot(hypot(x, y), z)
    return floatArrayOf(x / l, y / l, z / l)
}

// 여행 덩어리가 모이는 자리(화면용 고정 위치, 실제 지도와는 무관)
private val Centers = listOf(
    norm(.75f, .35f, .55f), norm(-.6f, .55f, .55f), norm(.15f, -.75f, .65f), norm(-.55f, -.45f, -.7f),
    norm(.6f, .5f, -.62f), norm(-.95f, .05f, -.1f), norm(.2f, .15f, .97f), norm(-.2f, .9f, .3f),
    norm(.4f, -.3f, -.85f), norm(-.7f, -.7f, .2f), norm(.85f, -.35f, .1f),
)

private class TripSim {
    private val vx = FloatArray(N)
    private val vy = FloatArray(N)
    private val vz = FloatArray(N)
    private val phase = FloatArray(N)
    private val sizeK = FloatArray(N)
    private val scatterK = FloatArray(N)
    private val pick = FloatArray(N)
    private val lit = BooleanArray(N)
    private val trip = IntArray(N) { -1 }

    private var tripRgb = emptyList<FloatArray>()
    private var tripAt = FloatArray(0)
    private var tripC = emptyList<FloatArray>()

    private val px = FloatArray(N)
    private val py = FloatArray(N)
    private val pz = FloatArray(N)
    private val pr = FloatArray(N)
    private val pc = LongArray(N)
    private val order = IntArray(N) { it }

    var rot = .4f
    var scatter = 0f
    var t = 0f

    init {
        val rnd = Seeded(23)
        val golden = (PI * (3 - sqrt(5.0))).toFloat()
        for (i in 0 until N) {
            val y = 1 - 2 * (i + .5f) / N
            val r = sqrt(1 - y * y)
            val th = golden * i
            vx[i] = cos(th) * r
            vy[i] = y
            vz[i] = sin(th) * r
            phase[i] = rnd.next() * 6.28f
            sizeK[i] = .8f + rnd.next() * .45f
            scatterK[i] = .4f + rnd.next()
            pick[i] = rnd.next()
        }
    }

    /** 가까운 여행 중심에 닿는 방울은 그 여행, 나머지는 잉크(평소 사진). 일부만 볼 때는 방울도 일부만. */
    fun setup(trips: List<TripDot>, partial: Boolean) {
        tripRgb = trips.map { floatArrayOf(it.color.red * 255, it.color.green * 255, it.color.blue * 255) }
        tripAt = FloatArray(trips.size) { trips[it].at }
        tripC = trips.indices.map { k -> Centers[(k + if (partial) 6 else 0) % Centers.size] }
            .toMutableList().also { if (partial && it.size > 1) it[1] = norm(.55f, .55f, .62f) }
        val visible = if (partial) maxOf(12, (N * .16f).roundToInt()) else N
        lit.fill(false)
        (0 until N).sortedBy { pick[it] }.take(visible).forEach { lit[it] = true }
        val reach = if (partial) .55f else .86f
        for (i in 0 until N) {
            trip[i] = -1
            if (!lit[i]) continue
            var best = reach
            tripC.forEachIndexed { k, c ->
                val d = c[0] * vx[i] + c[1] * vy[i] + c[2] * vz[i]
                if (d > best) {
                    best = d
                    trip[i] = k
                }
            }
        }
    }

    fun tick(dt: Float, p: Float) {
        t += dt
        rot += dt * if (p >= 1f) .2f else .32f
        scatter *= exp(ln(.12f) * dt)
    }

    private fun clamp(x: Float) = x.coerceIn(0f, 1f)
    private fun ease(x: Float): Float {
        val c = clamp(x)
        return c * c * (3 - 2 * c)
    }

    fun DrawScope.draw(p: Float) {
        val d = size.minDimension
        val c = d / 2
        val radius = d * .4f
        val unit = 15f * d / 720f
        val cr = cos(rot)
        val sr = sin(rot)
        val ca = cos(.38f)
        val sa = sin(.38f)
        val read = 1 - clamp((p - .3f) / .1f) // 시간 띠는 장소 단계로 넘어가며 사라진다
        val calm = ease((p - .5f) / .2f)
        val rgb = FloatArray(3)
        for (i in 0 until N) {
            val k = trip[i]
            val tint = if (k >= 0) ease((p - tripAt[k] + .1f) / .1f) else 0f // 여행 색이 들고 …
            val pull = if (k >= 0) ease((p - tripAt[k] + .02f) / .08f) * .62f else 0f // … 덩어리로 뭉친다
            var x0 = vx[i]
            var y0 = vy[i]
            var z0 = vz[i]
            if (k >= 0) {
                val tc = tripC[k]
                x0 += (tc[0] - x0) * pull
                y0 += (tc[1] - y0) * pull
                z0 += (tc[2] - z0) * pull
                val l = sqrt(x0 * x0 + y0 * y0 + z0 * z0)
                x0 /= l; y0 /= l; z0 /= l
            }
            val rr = 1 + .03f * sin(t * 1.4f + phase[i]) + scatter * scatterK[i] * .5f
            val x = x0 * cr - z0 * sr
            var z = x0 * sr + z0 * cr
            val y = y0 * ca - z * sa
            z = y0 * sa + z * ca
            val depth = (z + 1) / 2
            val wave = if (lit[i]) maxOf(0f, sin(t * 2.6f - vy[i] * 4.5f)) * read else 0f
            for (j in 0..2) {
                val base = if (lit[i]) Ink[j] + (SunRgb[j] - Ink[j]) * wave else Ghost[j]
                rgb[j] = when {
                    k >= 0 -> base + (tripRgb[k][j] - base) * tint
                    lit[i] -> base + (Dim[j] - base) * calm // 평소 사진은 연한 회색으로 물러남
                    else -> base
                }
            }
            val f = min(.8f, (1 - depth) * .6f)
            px[i] = c + x * radius * rr
            py[i] = c + y * radius * rr
            pz[i] = z
            pr[i] = unit * sizeK[i] * (.45f + .7f * depth) * (1 + wave * .3f) * (1 + pull * .7f)
            pc[i] = Color(
                (rgb[0] + (Paper[0] - rgb[0]) * f) / 255f,
                (rgb[1] + (Paper[1] - rgb[1]) * f) / 255f,
                (rgb[2] + (Paper[2] - rgb[2]) * f) / 255f,
            ).value.toLong()
        }
        for (a in 1 until N) {
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
}

/**
 * 여행 지구본. 앨범 지구본의 잉크 방울이 [progress](0–1)에 따라 스스로 정리된다:
 * 노란 띠가 훑고(시간) → 방울에 색이 들고(장소) → 여행마다 덩어리로 뭉친다. 평소 사진은 연한 회색으로 물러난다.
 */
@Composable
fun TripGlobe(
    trips: List<TripDot>,
    partial: Boolean,
    progress: () -> Float,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
) {
    val sim = remember { TripSim() }
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val currentProgress by rememberUpdatedState(progress)
    var frame by remember { mutableLongStateOf(0L) }

    remember(trips, partial) { sim.setup(trips, partial) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && now - last < FRAME_INTERVAL_NS) return@withFrameNanos
                val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f)
                last = now
                sim.tick(dt, currentProgress())
                frame = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .gooey(blurSigma = 6.dp, keepColors = true)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "방울 흩뜨리기",
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                sim.scatter = 1.1f
            },
    ) {
        frame // 프레임마다 다시 그리도록 읽기
        with(sim) { draw(currentProgress()) }
    }
}
