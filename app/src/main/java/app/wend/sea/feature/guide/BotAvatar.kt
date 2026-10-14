package app.wend.sea.feature.guide

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos

private class BotLook(
    val stops: Array<Pair<Float, Color>>,
    val gradientCenter: Offset, // viewBox(100) 좌표
    val gradientRadius: Float,
    val eyeY: Float,
    val eyeColor: Color,
    // 두 봇이 동시에 깜빡이지 않도록 어긋나게 하는 시간(초)
    val breatheOffset: Float,
    val blinkDelay: Float,
    val lookDelay: Float,
)

private val TickLook = BotLook(
    stops = arrayOf(0f to Color(0xFFFFE29A), .55f to Color(0xFFF2B33D), 1f to Color(0xFFC9841C)),
    gradientCenter = Offset(6f + .38f * 88f, 6f + .30f * 88f),
    gradientRadius = .75f * 88f,
    eyeY = 50f,
    eyeColor = Color(0xFF2A1D10),
    breatheOffset = 0f,
    blinkDelay = 0f,
    lookDelay = 0f,
)

private val PinLook = BotLook(
    stops = arrayOf(0f to Color(0xFFFFAB93), .55f to Color(0xFFE2553A), 1f to Color(0xFFA8301B)),
    gradientCenter = Offset(14f + .38f * 72f, 6f + .32f * 92f),
    gradientRadius = .75f * 82f,
    eyeY = 60f,
    eyeColor = Color(0xFF2B0E07),
    breatheOffset = 1.4f,
    blinkDelay = 1.7f,
    lookDelay = 2.4f,
)

// 핀: 위가 뾰족한 물방울
private val PinBody = Path().apply {
    moveTo(50f, 6f)
    cubicTo(58f, 20f, 86f, 42f, 86f, 62f)
    arcTo(Rect(14f, 26f, 86f, 98f), 0f, 180f, false)
    cubicTo(14f, 42f, 42f, 20f, 50f, 6f)
    close()
}

/**
 * 봉제인형 느낌의 봇 아바타. 숨쉬기 · 눈 깜빡임 · 두리번거림을 하고,
 * 두 봇은 타이밍을 어긋나게 해서 동시에 깜빡이지 않는다.
 * [clock] 은 화면이 공유하는 저주사율 시계(초) — 그리기 단계에서만 읽는다.
 */
@Composable
fun BotAvatar(bot: Bot, clock: State<Float>, modifier: Modifier = Modifier) {
    val look = if (bot == Bot.Tick) TickLook else PinLook
    Canvas(modifier) {
        val t = clock.value
        val unit = size.minDimension / 100f
        scale(unit, unit, pivot = Offset.Zero) {
            // 숨쉬기(3.2초): 아래 가운데를 기준으로 살짝 늘었다 줄었다
            val b = easeLoop((t + look.breatheOffset) / 3.2f)
            withTransform({ scale(1f + .03f * b, 1f - .03f * b, pivot = Offset(50f, 96f)) }) {
                drawBody(bot, look)
            }
            val lookT = progress(t - look.lookDelay, 7f)
            translate(lookX(lookT), lookY(lookT)) {
                withTransform({ scale(1f, blink(progress(t - look.blinkDelay, 4.6f)), pivot = Offset(50f, look.eyeY)) }) {
                    drawEyes(bot, look)
                }
            }
        }
    }
}

/** 0→1→0 을 부드럽게 오가는 값 (ease-in-out 왕복) */
fun easeLoop(cycles: Float): Float = ((1 - cos(cycles * 2 * PI)) / 2).toFloat()

private fun progress(t: Float, period: Float): Float = if (t <= 0f) 0f else (t % period) / period

private fun lerpAt(p: Float, from: Float, to: Float, a: Float, b: Float): Float {
    val f = ((p - from) / (to - from)).coerceIn(0f, 1f)
    val eased = easeLoop(f / 2) // 0→1 ease-in-out
    return a + (b - a) * eased
}

// 46~50% 구간에서 눈을 감았다 뜬다
private fun blink(p: Float): Float = when {
    p < .46f || p > .50f -> 1f
    p < .48f -> 1f - .9f * (p - .46f) / .02f
    else -> .1f + .9f * (p - .48f) / .02f
}

// 30~38% 오른쪽 위 → 55~63% 왼쪽 → 80~88% 가운데
private fun lookX(p: Float): Float = when {
    p < .30f -> 0f
    p < .38f -> lerpAt(p, .30f, .38f, 0f, 3f)
    p < .55f -> 3f
    p < .63f -> lerpAt(p, .55f, .63f, 3f, -3f)
    p < .80f -> -3f
    p < .88f -> lerpAt(p, .80f, .88f, -3f, 0f)
    else -> 0f
}

private fun lookY(p: Float): Float = when {
    p < .30f -> 0f
    p < .38f -> lerpAt(p, .30f, .38f, 0f, -1f)
    p < .55f -> -1f
    p < .63f -> lerpAt(p, .55f, .63f, -1f, 0f)
    else -> 0f
}

private fun DrawScope.drawBody(bot: Bot, look: BotLook) {
    val brush = Brush.radialGradient(*look.stops, center = look.gradientCenter, radius = look.gradientRadius)
    if (bot == Bot.Tick) {
        // 틱: 클로버 — 원 다섯 개
        listOf(Offset(50f, 28f), Offset(28f, 52f), Offset(72f, 52f), Offset(50f, 72f))
            .forEach { drawCircle(brush, radius = 22f, center = it) }
        drawCircle(brush, radius = 24f, center = Offset(50f, 50f))
    } else {
        drawPath(PinBody, brush)
    }
}

private fun DrawScope.drawEyes(bot: Bot, look: BotLook) {
    val y = look.eyeY
    listOf(40f, 60f).forEach { x ->
        drawOval(look.eyeColor, topLeft = Offset(x - 4.6f, y - 6.4f), size = Size(9.2f, 12.8f))
        drawCircle(Color.White, radius = 1.6f, center = Offset(x + 1.6f, y - 2.4f))
    }
    if (bot == Bot.Pin) {
        val smile = Path().apply {
            moveTo(45f, 71f)
            quadraticTo(50f, 75f, 55f, 71f)
        }
        drawPath(smile, look.eyeColor, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
    }
}
