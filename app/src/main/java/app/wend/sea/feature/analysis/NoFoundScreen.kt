package app.wend.sea.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.BotBubble
import app.wend.sea.core.designsystem.component.BubbleMaxWidth
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.feature.permission.PermBottomBar
import app.wend.sea.feature.permission.PermHeading
import app.wend.sea.feature.permission.rememberMediaPermissionRequest

@Composable
fun NoFoundScreen(
    onReanalyze: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    val partial = summary?.partial == true
    val currentOnReanalyze by rememberUpdatedState(onReanalyze)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val clock = rememberAmbientClock()
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.checkAccess() },
    )
    val allowAll = {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        if (permission.canShowDialog()) permission.request() else permission.openSettings()
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                ResultEvent.Reanalyze -> currentOnReanalyze()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PermBottomBar {
                if (partial) {
                    WendPrimaryButton(text = "모든 사진 허용하기", onClick = allowAll)
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.onBrowse()
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) { Text("이대로 둘러볼게요", style = MaterialTheme.typography.labelLarge) }
                } else {
                    WendPrimaryButton(
                        text = "확인했어요",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.onBrowse()
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            PermHeading(AnnotatedString("아직 여행을\n찾지 못했어요"), modifier = Modifier.padding(top = 24.dp))
            if (summary != null) {
                Text(
                    if (partial) {
                        "고르신 ${formatCount(summary.total)}장으로는 여행을 찾기 어려웠어요."
                    } else {
                        "사진·영상 ${formatCount(summary.total)}개를 모두 읽었지만 여행으로 묶을 사진이 없었어요."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            TripStage(
                trips = emptyList(),
                partial = partial,
                progress = { 1f },
                height = 224.dp,
                globeSize = 212.dp,
                circle = 194.dp,
                description = "색이 들지 않은 회색 방울만 남은 지구본",
            )
            // 원인과 할 수 있는 일만 말하고 판단 기준은 드러내지 않는다
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(Bot.Pin, clock, Modifier.size(44.dp))
                Column(Modifier.widthIn(max = BubbleMaxWidth)) {
                    Text(
                        text = Bot.Pin.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
                    BotBubble(
                        if (partial) {
                            "여행은 사진이 여러 장 이어져야 보여요. 모든 사진을 허용해 주시면 처음부터 다시 찾아볼게요."
                        } else {
                            "사진에 위치가 남아 있지 않으면 여행을 찾기 어려워요. 카메라에서 위치 태그를 켜 두시면 다음 여행부터 찾아 드릴게요."
                        },
                    )
                }
            }
            if (!partial) LocationTagSteps(Modifier.padding(top = 16.dp))
        }
    }
}

/** 시안 `#nf-steps`: 카메라 > 설정 > 위치 태그 켜기 */
@Composable
private fun LocationTagSteps(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            appendInlineContent("camera", "카메라")
            append(" 카메라 ")
            appendInlineContent("chevron", ">")
            append(" 설정 ")
            appendInlineContent("chevron", ">")
            append(" ")
            withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append("위치 태그") }
            append(" 켜기")
        },
        inlineContent = mapOf(
            "camera" to inlineIcon(Icons.Outlined.PhotoCamera),
            "chevron" to inlineIcon(Icons.Rounded.ChevronRight),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

private fun inlineIcon(icon: ImageVector) = InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
