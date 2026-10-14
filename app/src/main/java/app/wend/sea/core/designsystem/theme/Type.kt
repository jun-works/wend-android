package app.wend.sea.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.wend.sea.R

// Gowun Batang / IBM Plex Sans KR 의 세로 metric 만 CSS 와 같게 고친 버전 (tools/fonts/README.md)
val GowunBatang = FontFamily(
    Font(R.font.wend_batang_regular, FontWeight.Normal),
    Font(R.font.wend_batang_bold, FontWeight.Bold),
)

val IbmPlexSansKr = FontFamily(
    Font(R.font.wend_sans_kr_regular, FontWeight.Normal),
    Font(R.font.wend_sans_kr_medium, FontWeight.Medium),
    Font(R.font.wend_sans_kr_semi_bold, FontWeight.SemiBold),
    Font(R.font.wend_sans_kr_bold, FontWeight.Bold),
)

private fun display(size: Int, line: Double, spacing: Double = 0.0) = TextStyle(
    fontFamily = GowunBatang,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = spacing.sp,
    // 한국어 제목이 글자 단위로 끊기지 않게 어절 단위 + 균형 줄바꿈
    lineBreak = LineBreak.Heading,
    lineHeightStyle = CssLineHeight,
)

private fun body(size: Int, line: Number, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = IbmPlexSansKr,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.toFloat().sp,
    lineHeightStyle = CssLineHeight,
)

/**
 * 시안(CSS)과 같은 방식으로 줄 높이를 채운다: 남는 높이를 글자 위아래에 똑같이 나눈다.
 * 기본값(Proportional)은 폰트 ascent/descent 비율로 나눠서 한글 폰트에서 위아래 여백이 달라지고,
 * 폰트 높이가 줄높이보다 크면 Android 가 줄을 늘리므로, 번들 폰트는 높이를 1em 으로 맞춰 두었다.
 */
val CssLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
    )

val Typography = Typography(
    displayLarge = display(44, 1.0, -0.5),
    headlineLarge = display(32, 1.3, -0.4),
    headlineMedium = display(28, 1.32, -0.3),
    // 시안 토큰 title-l: 400 22px/28px 세리프
    titleLarge = display(22, 28.0 / 22).copy(fontWeight = FontWeight.Normal),
    titleMedium = body(16, 24, FontWeight.Medium),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 20),
    bodySmall = body(12, 16),
    labelLarge = body(14, 20, FontWeight.Medium),
    labelMedium = body(12, 16, FontWeight.Medium),
    labelSmall = body(11, 16, FontWeight.Medium),
)
