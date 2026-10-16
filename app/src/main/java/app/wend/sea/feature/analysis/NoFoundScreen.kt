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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.BotBubble
import app.wend.sea.core.designsystem.component.BubbleMaxWidth
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.core.text.appendTemplate
import app.wend.sea.feature.permission.PermBottomBar
import app.wend.sea.feature.permission.PermHeading
import app.wend.sea.feature.permission.rememberMediaPermissionRequest

@Composable
fun NoFoundScreen(
    onReanalyze: () -> Unit,
    onOpenTrips: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val currentOnOpenTrips by rememberUpdatedState(onOpenTrips)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 사진을 모두 허용했으면 처음부터 다시 찾을지 묻는다(다시 찾기는 초기화)
    if (uiState.confirmReanalyze) ReanalyzeDialog(onConfirm = viewModel::onReanalyzeConfirmed, onDismiss = viewModel::onReanalyzeDismissed)
    val summary = uiState.summary
    val partial = summary?.partial == true
    val currentOnReanalyze by rememberUpdatedState(onReanalyze)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val clock = rememberAmbientClock()
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.checkAccess(fromUser = true) },
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
                ResultEvent.OpenTrips -> currentOnOpenTrips()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PermBottomBar {
                if (partial) {
                    WendPrimaryButton(text = stringResource(R.string.allow_all_photos), onClick = allowAll)
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.onBrowse()
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) { Text(stringResource(R.string.nofound_browse_anyway), style = MaterialTheme.typography.labelLarge) }
                } else {
                    WendPrimaryButton(
                        text = stringResource(R.string.nofound_ok),
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
            PermHeading(AnnotatedString(stringResource(R.string.nofound_title)), modifier = Modifier.padding(top = 24.dp))
            if (summary != null) {
                Text(
                    pluralStringResource(
                        if (partial) R.plurals.nofound_summary_partial else R.plurals.nofound_summary,
                        summary.total,
                        formatCount(summary.total),
                    ),
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
                description = stringResource(R.string.nofound_globe_description),
            )
            // 원인과 할 수 있는 일만 말하고 판단 기준은 드러내지 않는다
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(Bot.Pin, clock, Modifier.size(44.dp))
                Column(Modifier.widthIn(max = BubbleMaxWidth)) {
                    Text(
                        text = stringResource(Bot.Pin.title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
                    BotBubble(stringResource(if (partial) R.string.nofound_bot_partial else R.string.nofound_bot))
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
    val steps = stringResource(R.string.nofound_steps)
    val camera = stringResource(R.string.guide_card_camera)
    val locationTag = stringResource(R.string.location_tag)
    Text(
        text = buildAnnotatedString {
            appendTemplate(
                steps,
                { appendInlineContent("camera", camera) },
                { appendInlineContent("chevron", ">") },
                { withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(locationTag) } },
            )
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
