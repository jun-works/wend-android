package app.wend.sea.feature.globe

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.WendTopAppBar
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.wend.sea.core.text.appendTemplate

@Composable
fun GlobeScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: GlobeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    GlobeContent(
        uiState = uiState,
        onBack = onBack,
        onNext = onNext,
        onStepSelected = { step ->
            if (step != uiState.step) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            viewModel.onStepSelected(step)
        },
    )
}

@Composable
private fun GlobeContent(
    uiState: GlobeUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStepSelected: (GlobeStep) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.surface,
        topBar = { WendTopAppBar(onBack = onBack) },
        bottomBar = {
            WendPrimaryButton(
                text = stringResource(R.string.globe_next),
                onClick = onNext,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            val title = stringResource(R.string.globe_title)
            val titleEm = stringResource(R.string.globe_title_em)
            Text(
                text = buildAnnotatedString {
                    appendTemplate(
                        title,
                        { append(uiState.nickname) },
                        { withStyle(SpanStyle(color = colors.primary)) { append(titleEm) } },
                    )
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 시안 .gl-stage: 좌우 여백 없이 화면 전체 폭
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val bleed = 24.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = constraints.maxWidth + bleed * 2, maxWidth = constraints.maxWidth + bleed * 2),
                        )
                        layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                    }
                    .weight(1f)
                    .heightIn(min = 340.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlobeGuides()
                OrbGlobe(step = uiState.step)
                Text(
                    text = stringResource(R.string.globe_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                )
            }
            // 좁은 화면에서 일부 라벨만 두 줄이 돼도 세 카드 높이를 가장 높은 카드에 맞춘다
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 8.dp),
            ) {
                GlobeStep.entries.forEach { step ->
                    StepCard(
                        step = step,
                        selected = step == uiState.step,
                        onClick = { onStepSelected(step) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** 3단계에서 점이 지구본 모양을 벗어나도 형태가 읽히도록 뒤에 까는 점선 원 + 적도 타원 */
@Composable
private fun GlobeGuides() {
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(300.dp)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = dash)
        drawCircle(line, style = stroke)
        val equator = 96.dp.toPx()
        drawOval(
            color = line,
            topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - equator) / 2),
            size = androidx.compose.ui.geometry.Size(size.width, equator),
            alpha = .7f,
            style = stroke,
        )
    }
}

@Composable
private fun StepCard(
    step: GlobeStep,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.onSurface else colors.surfaceContainerLow, label = "stepBg")
    val content by animateColorAsState(if (selected) colors.surface else colors.onSurfaceVariant, label = "stepFg")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        // 시안 button 처럼 카드 높이가 늘어나도 내용은 세로 가운데
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = step.number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp, lineHeight = 11.sp),
                color = content.copy(alpha = if (selected) .75f else .7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(step.label), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
