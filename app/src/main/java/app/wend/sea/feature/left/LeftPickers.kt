package app.wend.sea.feature.left

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wend.sea.core.designsystem.component.tripColor
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.data.analysis.TripChoice
import app.wend.sea.feature.analysis.formatCount
import app.wend.sea.feature.analysis.name
import app.wend.sea.feature.analysis.period
import java.time.Instant
import java.time.ZoneId

/** 시안 대화상자·시트의 글자 버튼과 "가장 가까워요": 종이색 위 대비 4.5:1 을 넘는 진한 코랄 */
internal val PickAccent = Color(0xFF9A2A14)

/** "같은 기간" · "하루 차이" · "12일 차이" · "3달 차이" */
internal fun gapLabel(days: Int): String = when {
    days == 0 -> "같은 기간"
    days == 1 -> "하루 차이"
    days < 60 -> "${days}일 차이"
    else -> "${Math.round(days / 30f)}달 차이"
}

/** 고른 사진의 날짜 범위: "2026. 6. 20", "2026. 6. 20 – 6. 22", 해가 다르면 둘 다 연도 */
internal fun rangeText(times: List<Long>): String {
    val zone = ZoneId.systemDefault()
    val a = Instant.ofEpochMilli(times.min()).atZone(zone).toLocalDate()
    val b = Instant.ofEpochMilli(times.max()).atZone(zone).toLocalDate()
    val from = "${a.year}. ${a.monthValue}. ${a.dayOfMonth}"
    return when {
        a == b -> from
        a.year == b.year -> "$from – ${b.monthValue}. ${b.dayOfMonth}"
        else -> "$from – ${b.year}. ${b.monthValue}. ${b.dayOfMonth}"
    }
}

/** "부산 여행", 이름이 이미 "…여행"이면 그대로("7월 9일 여행") */
internal fun tripPhrase(name: String): String = if (name.endsWith("여행")) name else "$name 여행"

/** 위치가 없을 때 새 여행 이름: 첫 날짜로 "7월 9일 여행" */
internal fun dateTripName(times: List<Long>): String {
    val d = Instant.ofEpochMilli(times.min()).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${d.monthValue}월 ${d.dayOfMonth}일 여행"
}

/**
 * 시안 `#add-sheet`: 고른 사진을 넣을 여행. 시간이 가까운 여행부터, 맨 위는 "가장 가까워요".
 * 여행 친구에게 받은 사진처럼 여행 직후 사진을 바로 넣을 수 있게.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToTripSheet(count: Int, choices: List<TripChoice>, onPick: (TripChoice) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp)) {
            Text(
                "${count}장을 어느 여행에 넣을까요?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(choices, key = { _, c -> c.trip.number }) { i, choice ->
                    TripRow(choice, nearest = i == 0 && choice.gapDays != null, onClick = { onPick(choice) })
                }
            }
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = PickAccent),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 8.dp),
            ) { Text("취소") }
        }
    }
}

/** 시안 `.at-item`: 여행 색 방울 · 이름/기간 · 날짜 차이 */
@Composable
private fun TripRow(choice: TripChoice, nearest: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val trip = choice.trip
    val color = tripColor(trip.number)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (nearest) Modifier.background(colors.surfaceContainerLow) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.padding(end = 6.dp).size(22.dp)) {
            Box(Modifier.size(22.dp).background(color, CircleShape))
            Box(Modifier.offset(x = 19.dp, y = (-5).dp).size(10.dp).background(color, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(trip.name, style = RowNameStyle, color = colors.onSurface)
            Text(
                "${trip.period} · ${formatCount(trip.mediaCount)}장",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            choice.gapDays?.let { (if (nearest) "가장 가까워요 · " else "") + gapLabel(it) }.orEmpty(),
            style = MaterialTheme.typography.labelMedium.let { if (nearest) it.copy(fontWeight = FontWeight.SemiBold) else it },
            color = if (nearest) PickAccent else colors.onSurfaceVariant,
        )
    }
}

/**
 * 시안 `#new-trip`: 고른 사진으로 새 여행. 이름은 미리 채워 두고(사진에 장소가 있으면 가장 많은 도시, 없으면 날짜),
 * 날짜 범위·장수는 자동. 빈 이름이면 미리 채운 이름으로 만든다.
 */
@Composable
fun NewTripDialog(context: String, suggested: String, onMake: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(suggested, TextRange(0, suggested.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AddLocationAlt, contentDescription = null) },
        title = { Text("새 여행 만들기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(context, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                TextField(
                    value = value,
                    onValueChange = { if (it.text.length <= NAME_MAX) value = it },
                    label = { Text("여행 이름") },
                    supportingText = { Text("나중에 바꿀 수 있어요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMake(value.text.trim().ifEmpty { suggested }) },
                colors = ButtonDefaults.textButtonColors(contentColor = PickAccent),
            ) { Text("만들기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = PickAccent)) { Text("취소") }
        },
    )
}

/** 시안 `#nt-name` maxlength — 여행 이름 바꾸기와 같다 */
private const val NAME_MAX = 14

private val RowNameStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.8.sp, lineHeightStyle = CssLineHeight)
