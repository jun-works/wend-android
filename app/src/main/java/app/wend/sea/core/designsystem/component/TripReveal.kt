package app.wend.sea.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * 여행 = 색 방울: 목록 카드의 방울에서 여행 색 원이 퍼지며 화면을 채우고, 원 안에서 색이 걷히며 상세가 드러난다.
 * 뒤로 가면 반대로 방울로 오므라든다. [progress] 0 = 방울, 1 = 화면 전체. [origin] 은 방울 중심(화면 좌표, 없으면 화면 가운데).
 */
@Composable
fun TripReveal(progress: Float, origin: Offset?, color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // 다 퍼진 뒤에도 같은 구조를 유지한다 — 구조가 바뀌면 안의 화면이 새로 그려져 깜빡인다
    Box(
        modifier.graphicsLayer {
            clip = progress < 1f
            shape = if (progress < 1f) RevealShape(origin, progress, 11.dp.toPx()) else RectangleShape
        },
    ) {
        content()
        // 앞쪽 40% 는 여행 색 방울, 그 뒤로 색이 걷히며 내용이 보인다
        val tint = (1f - ((progress - .4f) / .6f).coerceIn(0f, 1f))
        if (tint > 0f) Box(Modifier.fillMaxSize().background(color.copy(alpha = tint)))
    }
}

private class RevealShape(private val origin: Offset?, private val t: Float, private val startRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = origin?.takeIf { it.isSpecified } ?: Offset(size.width / 2, size.height / 2)
        // 가장 먼 모서리까지 덮는 반지름
        val far = maxOf(hypot(c.x, c.y), hypot(size.width - c.x, c.y), hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y))
        val r = startRadius + (far - startRadius) * t
        return Outline.Generic(Path().apply { addOval(Rect(c, r)) })
    }
}
