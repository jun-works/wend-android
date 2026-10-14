package app.wend.sea.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wend.sea.core.designsystem.component.TripDot
import app.wend.sea.core.designsystem.component.TripGlobe
import app.wend.sea.core.designsystem.component.tripColor
import app.wend.sea.data.analysis.Trip

/** 여행이 지구본에서 물들고 뭉치는 시점: 찍은 순서대로 진행 0.58–0.95 사이에 고르게 */
fun tripDots(trips: List<Trip>): List<TripDot> =
    trips.mapIndexed { k, t -> TripDot(tripColor(t.number), at = .58f + k * (.37f / trips.size)) }

/** 시안 `.ag-stage`: 화면 전체 폭, 점선 원 위에 여행 지구본 */
@Composable
fun TripStage(
    trips: List<Trip>,
    partial: Boolean,
    progress: () -> Float,
    height: Dp,
    globeSize: Dp,
    circle: Dp,
    description: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val px = 24.dp.roundToPx()
                val width = constraints.maxWidth + px * 2
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
            }
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        val line = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.size(circle)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawCircle(line, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
        }
        TripGlobe(
            trips = tripDots(trips),
            partial = partial,
            progress = progress,
            size = globeSize,
            description = description,
        )
    }
}
