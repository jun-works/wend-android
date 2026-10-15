package app.wend.sea.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 시안 ㉑: 상세의 `+N / 전체 보기` 타일 자리에서 종이 앨범이 펼쳐진다. [progress] 0 = 타일, 1 = 화면 전체.
 * [origin] 은 타일 자리(화면 좌표, 없으면 펼치지 않고 바로 보인다). [closing] 이면 끝 30% 동안 흐려지며 타일로 접힌다.
 */
@Composable
fun AlbumUnfold(progress: Float, origin: Rect?, closing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // 다 펼친 뒤에도 같은 구조를 유지한다 — 구조가 바뀌면 안의 화면이 새로 그려져 깜빡인다
    Box(
        modifier.graphicsLayer {
            val folding = origin != null && progress < 1f
            clip = folding
            shape = if (folding) UnfoldShape(origin!!, progress, 14.dp.toPx()) else RectangleShape
            // 시안: 접힐 때 불투명 1 → (70%) 1 → 0
            alpha = if (closing && origin != null) (progress / .3f).coerceIn(0f, 1f) else 1f
        },
    ) { content() }
}

private class UnfoldShape(private val origin: Rect, private val t: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = lerp(origin, Rect(0f, 0f, size.width, size.height), t)
        val corner = radius * (1f - t)
        return Outline.Rounded(RoundRect(r, CornerRadius(corner)))
    }
}
