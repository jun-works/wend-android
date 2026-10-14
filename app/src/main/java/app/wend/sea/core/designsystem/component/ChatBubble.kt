package app.wend.sea.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wend.sea.core.designsystem.theme.OrbColors
import app.wend.sea.core.designsystem.theme.OutlineVariant

val BotBubbleShape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
val BubbleMaxWidth = 290.dp

/** 봇 말풍선. `**굵게**` 표시를 굵은 글씨로 바꾸고, [isKey] 면 검은 강조 말풍선. */
@Composable
fun BotBubble(text: String, isKey: Boolean = false, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = if (isKey) colors.onSurface else colors.surfaceContainerLowest
    val content = if (isKey) colors.surface else colors.onSurface
    Box(modifier.paperCard(BotBubbleShape, background, underline = !isKey).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = buildAnnotatedString {
                if (isKey) {
                    appendInlineContent(INFO_ID, "ⓘ")
                }
                append(text.withBold())
            },
            inlineContent = if (isKey) InfoInline else emptyMap(),
            style = MaterialTheme.typography.bodyLarge,
            color = content,
        )
    }
}

private const val INFO_ID = "info"
private val InfoInline = mapOf(
    INFO_ID to InlineTextContent(
        Placeholder(24.sp, 20.sp, PlaceholderVerticalAlign.Center),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = OrbColors.Sun, modifier = Modifier.padding(end = 4.dp))
    },
)

/** 종이 카드: 배경 + 아래쪽 1dp 그림자 선 */
fun Modifier.paperCard(shape: Shape, color: Color, underline: Boolean = true) = this
    .drawBehind {
        if (underline) {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(top = 1.dp.toPx()) { drawOutline(outline, OutlineVariant) }
        }
    }
    .background(color, shape)

// 굵은 구간은 시안(.bub b{white-space:nowrap})처럼 중간에서 줄바꿈하지 않는다
fun String.withBold(): AnnotatedString = buildAnnotatedString {
    split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part.replace(' ', '\u00A0')) }
        } else {
            append(part)
        }
    }
}
