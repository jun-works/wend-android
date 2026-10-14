package app.wend.sea.feature.guide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.BotBubble
import app.wend.sea.core.designsystem.component.BotBubbleShape
import app.wend.sea.core.designsystem.component.BubbleMaxWidth
import app.wend.sea.core.designsystem.component.paperCard
import app.wend.sea.core.designsystem.component.easeLoop
import app.wend.sea.core.designsystem.component.WendChip
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.component.gooey
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.core.designsystem.component.spread
import app.wend.sea.core.designsystem.theme.IncludedGreen
import app.wend.sea.core.designsystem.theme.OrbColors

private val MyBubbleShape = RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
private val AvatarSize = 44.dp
private val PopEasing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)

@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    // 봇 말풍선이 도착할 때마다 톡 — 규칙 핵심 문장(검은 말풍선)은 한 단계 또렷하게
    val lastBotMessage = uiState.items.lastOrNull() as? ChatItem.BotMessage
    LaunchedEffect(lastBotMessage?.id) {
        val message = lastBotMessage ?: return@LaunchedEffect
        haptic.performHapticFeedback(
            if (message.isKey) HapticFeedbackType.SegmentTick else HapticFeedbackType.KeyboardTap,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GuideEvent.Finished -> currentOnFinished()
            }
        }
    }

    GuideContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onReplay = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewModel.onReplay()
        },
        onReplyClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewModel.onReplyClick(it)
        },
        onFinish = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.onFinish()
        },
    )
}

@Composable
private fun GuideContent(
    uiState: GuideUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onReplay: () -> Unit,
    onReplyClick: (QuickReply) -> Unit,
    onFinish: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val clock = rememberAmbientClock()

    // 새 말풍선이 붙을 때마다 맨 아래로
    LaunchedEffect(uiState.items.size, uiState.replies.size) {
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WendTopAppBar(
                onBack = onBack,
                title = {
                    // 시안 .cast: 28dp 아바타 두 개가 8dp 겹치고, 오른쪽 2dp + 제목 간격 8dp
                    Box(Modifier.size(width = 48.dp, height = 28.dp)) {
                        BotAvatar(Bot.Tick, clock, Modifier.size(28.dp))
                        BotAvatar(Bot.Pin, clock, Modifier.size(28.dp).offset(x = 20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("틱 & 핀")
                },
                actions = {
                    IconButton(onClick = onReplay) {
                        Icon(Icons.Rounded.Replay, contentDescription = "처음부터 다시 듣기", tint = colors.onSurfaceVariant)
                    }
                },
            )
        },
        bottomBar = {
            WendPrimaryButton(
                text = "알겠어요, 시작할게요",
                onClick = onFinish,
                enabled = uiState.canFinish,
                modifier = Modifier
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uiState.items.forEach { item ->
                androidx.compose.runtime.key(item.id) {
                    when (item) {
                        is ChatItem.Day -> DayDivider()
                        is ChatItem.BotMessage -> BotRow(item.bot, item.showHeader, clock) {
                            BotBubble(item.text, isKey = item.isKey)
                            if (item.withExampleCard) ExampleCard()
                        }
                        is ChatItem.Typing -> BotRow(item.bot, item.showHeader, clock) { TypingBubble(clock) }
                        is ChatItem.UserMessage -> MyBubble(item.text)
                    }
                }
            }
            if (uiState.replies.isNotEmpty()) {
                QuickReplies(uiState.replies, onReplyClick)
            }
        }
    }
}

@Composable
private fun DayDivider() {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "오늘 · 처음 만나는 날",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 같은 봇이 이어 말하면 아바타·이름을 생략하고 말풍선만 붙인다. */
@Composable
private fun BotRow(bot: Bot, showHeader: Boolean, clock: State<Float>, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .popIn()
            .padding(top = if (showHeader) 10.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeader) BotAvatar(bot, clock, Modifier.size(AvatarSize)) else Spacer(Modifier.width(AvatarSize))
        Column(
            modifier = Modifier.widthIn(max = BubbleMaxWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showHeader) {
                Text(
                    text = bot.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun MyBubble(text: String) {
    Box(Modifier.fillMaxWidth().popIn(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .background(MaterialTheme.colorScheme.primary, MyBubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** 입력 중 — 점 3개를 Gooey 로 묶어 위아래로 움직이면 서로 늘어붙는다. */
@Composable
private fun TypingBubble(clock: State<Float>) {
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.paperCard(BotBubbleShape, MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Box(Modifier.size(width = 42.dp, height = 18.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .spread(6.dp)
                    .gooey(blurSigma = 2.5.dp, alphaScale = 18f, alphaOffset = -7f),
            ) {
                val pad = 6.dp.toPx()
                val r = 5.dp.toPx()
                repeat(3) { i ->
                    // 1초 주기, 점마다 0.15초씩 늦게: 제자리 → 위로 6dp, 1.15배 → 제자리
                    val lift = easeLoop(clock.value - i * .15f)
                    drawCircle(
                        color = dotColor,
                        radius = r * (1 + .15f * lift),
                        center = Offset(pad + 4.dp.toPx() + r + i * (2 * r + 2.dp.toPx()), pad + 4.dp.toPx() + r - lift * 6.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** 담기는 사진(카메라) vs 담기지 않을 수 있는 사진(캡처) 예시 */
@Composable
private fun ExampleCard() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(BubbleMaxWidth)
            .paperCard(BotBubbleShape, colors.surfaceContainerLowest)
            .padding(14.dp),
    ) {
        CardLabel(Icons.Rounded.CheckCircle, "여행에 담겨요", IncludedGreen)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail("카메라", 78.dp) { SeaPhoto() }
            Column(Modifier.align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaLine(Icons.Outlined.Schedule, "2026. 7. 14 오후 6:32", IncludedGreen)
                MetaLine(Icons.Outlined.LocationOn, "제주 애월읍", IncludedGreen)
            }
        }
        DashedDivider(Modifier.padding(vertical = 14.dp))
        CardLabel(Icons.Rounded.Cancel, "여행에 담기지 않을 수 있어요", colors.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail("캡처", 64.dp) { ScreenshotPhoto() }
            MetaLine(Icons.Outlined.LocationOff, "위치 없음", colors.error, struck = true, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun CardLabel(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 12.sp), color = color)
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String, color: Color, struck: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.25.sp,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            ),
            color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
    }
}

@Composable
private fun Thumbnail(tag: String, size: Dp, art: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp))) {
        art()
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 11.sp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .background(Color(0xB81F1A16), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SeaPhoto() {
    Canvas(Modifier.fillMaxSize()) {
        val h = size.height
        drawRect(Color(0xFFF7C58A), size = Size(size.width, h * .38f))
        drawRect(Color(0xFFEF8A5B), topLeft = Offset(0f, h * .38f), size = Size(size.width, h * .08f))
        drawRect(OrbColors.Sea, topLeft = Offset(0f, h * .46f), size = Size(size.width, h * .54f))
        drawCircle(Color(0xFFFFF3D6), radius = 8.dp.toPx(), center = Offset(52.dp.toPx(), 26.dp.toPx()))
    }
}

@Composable
private fun ScreenshotPhoto() {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().background(colors.surfaceContainer)) {
        val left = 10.dp.toPx()
        val full = size.width - left * 2
        listOf(1f to colors.outlineVariant, .7f to colors.outlineVariant, .85f to colors.outlineVariant, .5f to OrbColors.Sun)
            .forEachIndexed { i, (w, c) ->
                drawRoundRect(
                    color = c,
                    topLeft = Offset(left, 12.dp.toPx() + i * 12.dp.toPx()),
                    size = Size(full * w, 6.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
    }
}

@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().size(height = 1.dp, width = 0.dp)) {
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReplies(replies: List<QuickReply>, onClick: (QuickReply) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .popIn(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        replies.forEach { reply ->
            WendChip(
                text = reply.question,
                onClick = { onClick(reply) },
                minHeight = 40.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }
    }
}

/** 말풍선이 톡 튀어 오르며 나타난다. */
@Composable
private fun Modifier.popIn(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(450, easing = PopEasing)) }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1 - p) * 12.dp.toPx()
        val s = .94f + .06f * p
        scaleX = s
        scaleY = s
    }
}
