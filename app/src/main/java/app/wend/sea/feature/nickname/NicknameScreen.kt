package app.wend.sea.feature.nickname

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.WendChip
import app.wend.sea.core.designsystem.component.gooey
import app.wend.sea.core.designsystem.component.spread
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.theme.OrbColors

private val FieldHeight = 64.dp
private val ButtonGap = 76.dp // 입력창 오른쪽 끝에서 버튼 방울이 분리되는 거리
private val GooSpread = 20.dp // blur 가 잘리지 않도록 그리기 영역을 바깥으로 넓힌 여유
private val StretchEasing = CubicBezierEasing(0.5f, 0f, 0.2f, 1.25f)
private val InputText = Color(0xFFFBF8F3)
private val Placeholder = Color(0xFFB9AD9F)

// 시안: 500 18px — 64dp 입력창 세로 가운데. 입력창은 스크롤 때문에 잘리므로 줄높이는 여유 있게 둔다
private val InputStyle = TextStyle(
    fontFamily = IbmPlexSansKr,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    lineHeightStyle = CssLineHeight,
    color = InputText,
)

@Composable
fun NicknameScreen(
    onSaved: () -> Unit,
    viewModel: NicknameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by rememberUpdatedState(onSaved)
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NicknameEvent.Saved -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    saved()
                }
                NicknameEvent.FocusInput -> focusRequester.requestFocus()
                NicknameEvent.Rejected -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    focusRequester.requestFocus()
                }
            }
        }
    }

    // 허용 안 되는 글자를 입력한 순간
    var lastHelp by remember { mutableStateOf(uiState.help) }
    LaunchedEffect(uiState.help) {
        if (uiState.help == NicknameHelp.InvalidChars && lastHelp != NicknameHelp.InvalidChars) {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
        lastHelp = uiState.help
    }

    NicknameContent(
        uiState = uiState,
        suggestions = viewModel.suggestions,
        focusRequester = focusRequester,
        onInputChange = viewModel::onInputChange,
        onSuggestionClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewModel.onSuggestionClick(it)
        },
        onSubmit = viewModel::onSubmit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NicknameContent(
    uiState: NicknameUiState,
    suggestions: List<String>,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
    ) {
        WendMark(modifier = Modifier.padding(top = 24.dp, bottom = 40.dp))
        Text(text = "처음 뵙네요.\n어떻게 불러드릴까요?", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "여행을 정리해 드릴 때 이 이름으로 불러드려요.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        GooeyNicknameField(
            value = uiState.input,
            ready = uiState.isValid,
            focusRequester = focusRequester,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val helpColor = if (uiState.isError) colors.error else colors.onSurfaceVariant
            Text(
                text = when (uiState.help) {
                    NicknameHelp.Default -> "2~10자, 한글·영문·숫자"
                    NicknameHelp.InvalidChars -> "한글·영문·숫자만 쓸 수 있어요"
                    NicknameHelp.TooShort -> "두 글자 이상 입력해 주세요"
                },
                style = MaterialTheme.typography.bodySmall,
                color = helpColor,
            )
            Text(
                text = "${uiState.input.length}/${NicknameUiState.MAX_LENGTH}",
                style = MaterialTheme.typography.bodySmall,
                color = helpColor,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "이런 이름은 어때요?",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                WendChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "닉네임과 사진은 이 기기 안에서만 쓰여요. 어디에도 올리지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** 시안 `.nk-mark`: 18dp 코랄 방울 + 8dp 간격 + 700 20px/1 세리프 워드마크 */
@Composable
private fun WendMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            // box-shadow 8px -6px 0 -3px: 지름 12dp 노란 방울이 오른쪽 위로 비어져 나오고 코랄 방울 뒤에 깔린다
            drawCircle(OrbColors.Sun, radius = 6.dp.toPx(), center = center + Offset(8.dp.toPx(), -6.dp.toPx()))
            drawCircle(OrbColors.Coral, radius = 9.dp.toPx())
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Wend",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp),
        )
    }
}

/**
 * 어두운 pill 입력창. 이름이 유효해지면 오른쪽에서 빨간 → 버튼이
 * 입력창에서 물방울처럼 늘어나며 분리된다.
 */
@Composable
private fun GooeyNicknameField(
    value: String,
    ready: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    // 버튼 방울이 입력창에서 떨어져 나오는 순간
    var wasReady by remember { mutableStateOf(ready) }
    LaunchedEffect(ready) {
        if (ready && !wasReady) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        wasReady = ready
    }
    val pillEnd by animateDpAsState(if (ready) ButtonGap else 0.dp, tween(550, easing = StretchEasing), label = "pillEnd")
    val dotScale by animateFloatAsState(if (ready) 1f else 0.6f, tween(550, easing = StretchEasing), label = "dotScale")
    val dotColor by animateColorAsState(if (ready) MaterialTheme.colorScheme.primary else ink, tween(550), label = "dotColor")
    val buttonFraction by animateFloatAsState(if (ready) 1f else 0f, tween(400, delayMillis = if (ready) 200 else 0), label = "button")

    // 추천 이름처럼 바깥에서 값이 바뀌면 커서를 끝으로 옮긴다. 같은 값이면 IME 조합 상태를 유지한다.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight),
    ) {
        // 방울 레이어 — 끈적하게 합쳐지는 모양만 담당하고 글자는 바깥에 둔다
        Canvas(
            modifier = Modifier
                .spread(GooSpread)
                .gooey(blurSigma = 8.dp, alphaScale = 22f, alphaOffset = -9f),
        ) {
            val spread = GooSpread.toPx()
            val h = FieldHeight.toPx()
            val pillWidth = size.width - spread * 2 - pillEnd.toPx()
            drawRoundRect(
                color = ink,
                topLeft = Offset(spread, spread),
                size = Size(pillWidth, h),
                cornerRadius = CornerRadius(h / 2),
            )
            drawCircle(
                color = dotColor,
                radius = h / 2 * dotScale,
                center = Offset(size.width - spread - h / 2, spread + h / 2),
            )
        }
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            singleLine = true,
            textStyle = InputStyle,
            cursorBrush = SolidColor(InputText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(end = pillEnd)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "닉네임" },
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("닉네임 입력", style = InputStyle.copy(color = Placeholder))
                    }
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(FieldHeight)
                .graphicsLayer {
                    alpha = buttonFraction
                    val s = 0.4f + 0.6f * buttonFraction
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .clickable(enabled = ready, onClickLabel = "다음", onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "다음",
                tint = Color.White,
            )
        }
    }
}
