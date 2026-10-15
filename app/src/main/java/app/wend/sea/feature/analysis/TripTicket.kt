package app.wend.sea.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wend.sea.core.designsystem.component.paperCard
import app.wend.sea.core.designsystem.component.tripColor
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.data.analysis.Trip
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 시안 .tk-main b: 700 17/1.2 세리프 · .tk-n: 600 14/1
private val NameStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.4.sp, lineHeightStyle = CssLineHeight)
private val CountStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 14.sp, lineHeightStyle = CssLineHeight)

/** 사용자가 바꾼 이름, 아니면 자동 이름 */
val Trip.name: String get() = userName ?: autoName

/** 자동으로 붙인 이름(도시나 나라), 근처에 도시가 없으면 "여행 N" */
val Trip.autoName: String get() = city ?: "여행 $number"

/** "2025. 6. 7 – 6. 10 · 4일", 하루면 "2025. 9. 13 · 당일" */
val Trip.period: String
    get() {
        val zone = ZoneId.systemDefault()
        val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(s, e).toInt() + 1
        val from = "${s.year}. ${s.monthValue}. ${s.dayOfMonth}"
        return when {
            days == 1 -> "$from · 당일"
            s.year == e.year -> "$from – ${e.monthValue}. ${e.dayOfMonth} · ${days}일"
            else -> "$from – ${e.year}. ${e.monthValue}. ${e.dayOfMonth} · ${days}일"
        }
    }

fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** 시안 `.tk`: 색 방울 + 이름(세리프) + 기간 + 점선 절취선 뒤 장수 */
@Composable
fun TripTicket(trip: Trip, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color = tripColor(trip.number)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 62.dp)
            .paperCard(RoundedCornerShape(16.dp), colors.surfaceContainerLowest)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.padding(horizontal = 2.dp).size(22.dp)) {
            Box(Modifier.size(22.dp).background(color, CircleShape))
            Box(Modifier.offset(x = 19.dp, y = (-5).dp).size(10.dp).background(color, CircleShape))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(trip.name, style = NameStyle, color = colors.onSurface)
            Text(
                trip.period,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        TicketStub(Modifier.fillMaxHeight()) {
            Text("${formatCount(trip.mediaCount)}장", style = CountStyle, modifier = Modifier.padding(start = 14.dp, end = 16.dp))
        }
    }
}

/** 점선 왼쪽 테두리 + 위아래 반원 홈 */
@Composable
private fun TicketStub(modifier: Modifier, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val notch = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier.drawBehind {
            drawLine(
                color = line,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
            val r = 6.dp.toPx()
            drawCircle(notch, radius = r, center = Offset(-1.dp.toPx(), 0f))
            drawCircle(notch, radius = r, center = Offset(-1.dp.toPx(), size.height))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

