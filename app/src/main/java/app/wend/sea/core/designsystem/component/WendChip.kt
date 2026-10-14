package app.wend.sea.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * 시안 `.chip`: 높이 32, 좌우 16, 8dp 라운드, outline-variant 1dp 테두리, label-l.
 * M3 칩은 최소 터치 영역 48dp 를 강제해서 줄 간격이 시안보다 16dp 벌어지므로 직접 그린다.
 */
@Composable
fun WendChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 32.dp,
    containerColor: Color = Color.Transparent,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(ChipShape)
            .background(containerColor)
            .border(BorderStroke(1.dp, colors.outlineVariant), ChipShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
}
