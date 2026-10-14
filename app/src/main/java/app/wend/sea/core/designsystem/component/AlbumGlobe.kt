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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val ALBUM_DOT_COUNT = 280
private const val FRAME_INTERVAL_NS = 1_000_000_000L / 60 - 2_000_000L // vsync 오차 여유
private val Paper = floatArrayOf(246f, 241f, 233f)
private val Ghost = floatArrayOf(214f, 202f, 185f)
private val Ink = floatArrayOf(58f, 50f, 43f)

/** 시안과 같은 난수열(Park–Miller)로 점마다 같은 크기·위상·선택 순서를 만든다 */
private class Seeded(seed: Long) {
    private var s = seed
    fun next(): Float {
        s = (s * 16807) % 2147483647
        return ((s - 1) / 2147483646.0).toFloat()
    }
}

private class AlbumSim(seed: Long) {
    val vx = FloatArray(ALBUM_DOT_COUNT)
    val vy = FloatArray(ALBUM_DOT_COUNT)
    val vz = FloatArray(ALBUM_DOT_COUNT)
    private val phase = FloatArray(ALBUM_DOT_COUNT)
    private val sizeK = FloatArray(ALBUM_DOT_COUNT)
    private val scatterK = FloatArray(ALBUM_DOT_COUNT)
    private val pick = FloatArray(ALBUM_DOT_COUNT)
    private val rank = FloatArray(ALBUM_DOT_COUNT)
    private val want = BooleanArray(ALBUM_DOT_COUNT)

    private val px = FloatArray(ALBUM_DOT_COUNT)
    private val py = FloatArray(ALBUM_DOT_COUNT)
    private val pz = FloatArray(ALBUM_DOT_COUNT)
    private val pr = FloatArray(ALBUM_DOT_COUNT)
    private val pc = LongArray(ALBUM_DOT_COUNT)
    private val order = IntArray(ALBUM_DOT_COUNT) { it }

    var rot = .6f
    var wave = 2f
    var scatter = 0f
    var t = 0f

    init {
        val rnd = Seeded(seed)
        val golden = (PI * (3 - sqrt(5.0))).toFloat()
        for (i in 0 until ALBUM_DOT_COUNT) {
            val y = 1 - 2 * (i + .5f) / ALBUM_DOT_COUNT
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

    /** [lit] 개의 방울을 앞쪽부터 파도처럼 진하게 채운다(0 이면 모두 옅게). */
    fun open(lit: Int, animate: Boolean) {
        val fx = sin(rot)
        val fz = cos(rot)
        for (i in 0 until ALBUM_DOT_COUNT) {
            want[i] = false
            rank[i] = (1 - (vx[i] * fx + vz[i] * fz)) / 2
        }
        // 일부만 볼 수 있을 때도 보이는 쪽에 조금 더 몰리도록 앞쪽 가중치를 섞는다
        (0 until ALBUM_DOT_COUNT)
            .sortedBy { pick[it] * .55f + rank[it] * .45f }
            .take(lit.coerceIn(0, ALBUM_DOT_COUNT))
            .forEach { want[it] = true }
        wave = if (animate) -.12f else 2f
    }

    fun tick(dt: Float) {
        t += dt
        rot += dt * .3f
        if (wave < 2f) wave = min(2f, wave + dt * .62f)
        scatter *= exp(ln(.12f) * dt)
    }

    fun DrawScope.draw() {
        val d = size.minDimension
        val c = d / 2
        val radius = d * .4f
        val unit = 15f * d / 720f
        val cr = cos(rot)
        val sr = sin(rot)
        val ca = cos(.38f)
        val sa = sin(.38f)
        for (i in 0 until ALBUM_DOT_COUNT) {
            var a = if (want[i]) ((wave - rank[i]) / .22f).coerceIn(0f, 1f) else 0f
            a = a * a * (3 - 2 * a)
            val rr = 1 + .03f * sin(t * 1.4f + phase[i]) + scatter * scatterK[i] * .5f
            val x = vx[i] * cr - vz[i] * sr
            var z = vx[i] * sr + vz[i] * cr
            val y = vy[i] * ca - z * sa
            z = vy[i] * sa + z * ca
            val depth = (z + 1) / 2
            px[i] = c + x * radius * rr
            py[i] = c + y * radius * rr
            pz[i] = z
            pr[i] = unit * sizeK[i] * (.45f + .7f * depth) * (1 + .55f * sin(PI.toFloat() * a) + .22f * a)
            // 옅은 방울(볼 수 없는 사진) → 잉크(볼 수 있는 사진), 뒤쪽은 종이색에 섞어 흐리게
            val f = min(.8f, (1 - depth) * .6f)
            fun ch(k: Int): Float {
                val v = Ghost[k] + (Ink[k] - Ghost[k]) * a
                return (v + (Paper[k] - v) * f) / 255f
            }
            pc[i] = Color(ch(0), ch(1), ch(2)).value.toLong()
        }
        for (a in 1 until ALBUM_DOT_COUNT) {
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
 * 앨범 지구본. 방울 280개가 기기 앨범을 뜻하고, 옅은 방울은 아직 볼 수 없는 사진, 진한 방울은 볼 수 있는 사진.
 * [lit] 개만큼 앞쪽부터 파도처럼 진해진다. 탭하면 흩어졌다 다시 모인다.
 */
@Composable
fun AlbumGlobe(
    lit: Int,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
) {
    val sim = remember { AlbumSim(seed = 11) }
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lit) { sim.open(lit, animate = !reducedMotion) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && now - last < FRAME_INTERVAL_NS) return@withFrameNanos
                val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f)
                last = now
                sim.tick(dt)
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
        with(sim) { draw() }
    }
}

/** 앨범 지구본이 채워지는 데 걸리는 시간. 숫자 카운트업을 여기에 맞춘다. */
const val ALBUM_FILL_MS = 1290
