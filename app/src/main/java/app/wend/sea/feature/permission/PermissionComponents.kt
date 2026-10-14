package app.wend.sea.feature.permission

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wend.sea.core.designsystem.component.AlbumGlobe

/** 볼 수 있는 사진(잉크) · 볼 수 없는 사진(옅은 종이색) 범례 색 */
val InkDot = Color(0xFF3A322B)
val GhostDot = Color(0xFFD6CAB9)

/** 시안 `.pm-h`: 700 28/1.32 세리프 */
@Composable
fun PermHeading(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium, modifier = modifier)
}

/** 시안 `.pm-sub`: 제목 아래 10dp, body-l */
@Composable
fun PermSub(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * 시안 `.ag-stage`: 화면 전체 폭(좌우 여백 밖까지), 지름 236dp 점선 원 위에 앨범 지구본,
 * 아래쪽에 범례.
 */
@Composable
fun AlbumStage(
    lit: Int,
    height: Dp,
    globeSize: Dp,
    description: String,
    caption: List<Pair<Color, String>> = emptyList(),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bleed(24.dp)
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        val line = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.size(236.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawCircle(line, style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash))
        }
        AlbumGlobe(lit = lit, size = globeSize, description = description)
        if (caption.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                caption.forEach { (color, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(color, CircleShape))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 권한 화면 하단: 위아래로 쌓는 버튼 영역(좌우 16, 위아래 12, 간격 4) */
@Composable
fun PermBottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** 부모의 좌우 여백 밖까지 넓힌다(시안 margin: 0 -24px) */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val width = constraints.maxWidth + px * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}
