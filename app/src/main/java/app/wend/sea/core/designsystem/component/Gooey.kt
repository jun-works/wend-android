package app.wend.sea.core.designsystem.component

import android.graphics.BlendMode
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 끈적하게 합쳐지는(Gooey) 효과.
 * blur → 알파 채널 threshold 로 가까운 도형끼리 경계가 액체처럼 이어진다.
 * [keepColors] 가 true 면 원본 도형을 그 위에 SRC_ATOP 으로 다시 얹어 색이 탁해지지 않게 한다.
 * Android 12(API 31) 미만에서는 효과 없이 원본 그대로 그린다.
 */
@Composable
fun Modifier.gooey(
    blurSigma: Dp,
    alphaScale: Float = 20f,
    alphaOffset: Float = -8f,
    keepColors: Boolean = false,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    // 시안(SVG feGaussianBlur)의 stdDeviation 기준 → Android blur radius 로 변환
    val blurPx = with(LocalDensity.current) { ((blurSigma.toPx() - 0.5f) / 0.57735f).coerceAtLeast(0.1f) }
    return graphicsLayer {
        val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
        val threshold = ColorMatrixColorFilter(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, alphaScale, alphaOffset * 255f,
            ),
        )
        val goo = RenderEffect.createColorFilterEffect(threshold, blurEffect)
        val effect = if (keepColors) {
            // 입력 없는 offset effect = 원본(SourceGraphic)
            RenderEffect.createBlendModeEffect(goo, RenderEffect.createOffsetEffect(0f, 0f), BlendMode.SRC_ATOP)
        } else {
            goo
        }
        renderEffect = effect.asComposeRenderEffect()
    }
}

/**
 * 부모가 준 크기보다 사방으로 [amount] 만큼 크게 그린다.
 * gooey blur 가 레이어 경계에서 잘리지 않도록 방울 레이어에 쓴다. 레이아웃 크기는 그대로다.
 */
fun Modifier.spread(amount: Dp) = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth + px * 2, constraints.maxHeight + px * 2),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-px, -px) }
}
