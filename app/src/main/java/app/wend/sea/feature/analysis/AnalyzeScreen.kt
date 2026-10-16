package app.wend.sea.feature.analysis

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.core.designsystem.component.rememberReducedMotion
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.text.appendTemplate
import app.wend.sea.data.MediaAccess
import app.wend.sea.feature.permission.PermHeading
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** 시안 길이: 실제 분석이 금방 끝나도 이야기가 보이도록 최소 이만큼 보여 준다 */
private const val STORY_SECONDS = 9.5f
private const val MAX_TICKETS = 4
private val DropEasing = CubicBezierEasing(.3f, 1.35f, .5f, 1f)

private class Status(val until: Float, val bots: List<Bot>, @StringRes val text: Int)

private val Statuses = listOf(
    Status(.3f, listOf(Bot.Tick), R.string.analyze_status_time),
    Status(.55f, listOf(Bot.Pin), R.string.analyze_status_place),
    Status(1.01f, listOf(Bot.Tick, Bot.Pin), R.string.analyze_status_group),
)

@Composable
fun AnalyzeScreen(
    onFinished: (foundAny: Boolean) -> Unit,
    onFailed: (MediaAccess) -> Unit,
    viewModel: AnalyzeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val clock = rememberAmbientClock()
    val trips = uiState.trips.orEmpty()
    val dots = remember(trips) { tripDots(trips) }
    val foundAny by rememberUpdatedState(trips.isNotEmpty())

    // 화면 진행 p: 시간에 따라 차오르되 실제 분석이 온 곳(reachable)을 넘지 않는다
    var progress by remember { mutableFloatStateOf(0f) }
    val reachable by rememberUpdatedState(uiState.reachable)
    LaunchedEffect(Unit) {
        val duration = if (reduced) 1f else STORY_SECONDS
        var last = 0L
        while (progress < 1f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                progress = min(reachable, progress + dt / duration).coerceAtLeast(progress)
            }
        }
        delay(1_100)
        currentOnFinished(foundAny)
    }
    LaunchedEffect(uiState.failedWith) { uiState.failedWith?.let(currentOnFailed) }

    val statusIndex by remember { derivedStateOf { Statuses.indexOfFirst { progress <= it.until } } }
    val shown by remember(dots) { derivedStateOf { dots.count { progress >= it.at } } }
    val read by remember(uiState.total) { derivedStateOf { (uiState.total * min(1f, progress / .92f)).roundToInt() } }
    LaunchedEffect(shown) { if (shown > 0) haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { AnalyzeNotes() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp),
        ) {
            val primary = MaterialTheme.colorScheme.primary
            val title = stringResource(R.string.analyze_title)
            val titleEm = stringResource(R.string.analyze_title_em)
            PermHeading(
                buildAnnotatedString {
                    appendTemplate(
                        title,
                        { append(uiState.nickname) },
                        { withStyle(SpanStyle(color = primary)) { append(titleEm) } },
                    )
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            StatusRow(Statuses[statusIndex.coerceAtLeast(0)], clock)
            TripStage(
                trips = trips,
                partial = uiState.partial,
                progress = { progress },
                height = 252.dp,
                globeSize = 244.dp,
                circle = 236.dp,
                description = stringResource(R.string.analyze_globe_description),
            )
            ReadRow(read = read, total = uiState.total, progress = { min(1f, progress / .92f) })
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.analyze_found_trips), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 14.sp))
                Text(
                    "$shown",
                    style = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp, lineHeightStyle = CssLineHeight),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (shown == 0) {
                Text(
                    stringResource(R.string.analyze_still_finding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                // 찍은 순서대로 발견되고, 최신이 위. 최신 4개만 남긴다.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trips.take(shown).reversed().take(MAX_TICKETS).forEach { trip ->
                        key(trip.number) { TripTicket(trip, Modifier.dropIn(reduced)) }
                    }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
}

/** 시안 `.an-status`: 지금 일하는 봇 + 진행 이야기 */
@Composable
private fun StatusRow(status: Status, clock: androidx.compose.runtime.State<Float>) {
    Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (status.bots.size == 1) {
            BotAvatar(status.bots[0], clock, Modifier.size(34.dp))
        } else {
            // 시안 .cast: 두 봇이 8dp 겹침
            Box(Modifier.size(width = 48.dp, height = 28.dp)) {
                BotAvatar(Bot.Tick, clock, Modifier.size(28.dp))
                BotAvatar(Bot.Pin, clock, Modifier.size(28.dp).offset(x = 20.dp))
            }
        }
        key(status.text) {
            Text(
                stringResource(status.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fadeUpIn(),
            )
        }
    }
}

/** 시안 `.an-read`: 읽은 사진 막대 + 수 */
@Composable
private fun ReadRow(read: Int, total: Int, progress: () -> Float) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.analyze_read_photos), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Canvas(Modifier.weight(1f).height(4.dp)) {
            val r = CornerRadius(2.dp.toPx())
            drawRoundRect(colors.surfaceContainerHigh, cornerRadius = r)
            drawRoundRect(colors.onSurface, size = Size(size.width * progress(), size.height), cornerRadius = r)
        }
        Text(
            "${formatCount(read)} / ${formatCount(total)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 92.dp),
        )
    }
}

/** 화면 맨 아래: 앱을 닫아도 계속된다는 안내 + 시안의 신뢰 문구 */
@Composable
private fun AnalyzeNotes() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoteLine(Icons.Outlined.Sync, stringResource(R.string.analyze_note_background))
        NoteLine(Icons.Outlined.PhonelinkLock, stringResource(R.string.analyze_note_private))
    }
}

@Composable
private fun NoteLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 시안 `.tk.drop`: 위에서 떨어지며 살짝 튀는 티켓 */
@Composable
private fun Modifier.dropIn(reduced: Boolean): Modifier {
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(600, easing = DropEasing)) }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1 - v) * -34.dp.toPx()
        val s = .9f + .1f * v
        scaleX = s
        scaleY = s
    }
}

/** 시안 fadeUp .4s */
@Composable
private fun Modifier.fadeUpIn(): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(400)) }
    return graphicsLayer {
        alpha = p.value
        translationY = (1 - p.value) * 10.dp.toPx()
    }
}

