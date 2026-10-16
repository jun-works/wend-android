package app.wend.sea.feature.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import app.wend.sea.data.MediaAccess

@Composable
fun DeniedScreen(
    onResult: (MediaAccess) -> Unit,
    viewModel: DeniedViewModel = hiltViewModel(),
) {
    val settingsOnly by viewModel.settingsOnly.collectAsStateWithLifecycle()
    val currentOnResult by rememberUpdatedState(onResult)
    val haptic = LocalHapticFeedback.current
    val clock = rememberAmbientClock()
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = viewModel::onReturnFromSettings,
    )

    LifecycleResumeEffect(viewModel) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PermissionEvent.ShowResult -> {
                    haptic.performResultFeedback(event.access)
                    currentOnResult(event.access)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            PermBottomBar {
                WendPrimaryButton(
                    text = stringResource(if (settingsOnly) R.string.denied_allow_in_settings else R.string.denied_allow_again),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (settingsOnly) permission.openSettings() else permission.request()
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            PermHeading(AnnotatedString(stringResource(R.string.denied_title)), modifier = Modifier.padding(top = 24.dp))
            AlbumStage(
                lit = 0,
                height = 236.dp,
                globeSize = 220.dp,
                description = stringResource(R.string.denied_globe_description),
            )
            // 시안 .dn-say: 온보딩의 핀이 다시 나와 안심시킨다
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(Bot.Pin, clock, Modifier.size(44.dp))
                Column(Modifier.widthIn(max = BubbleMaxWidth)) {
                    Text(
                        text = stringResource(Bot.Pin.title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
                    BotBubble(stringResource(if (settingsOnly) R.string.denied_bot_settings else R.string.denied_bot))
                }
            }
            if (settingsOnly) SettingsSteps(Modifier.padding(top = 16.dp))
        }
    }
}

/** 시안 `.dn-steps`: 설정 경로 안내 */
@Composable
private fun SettingsSteps(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val strong = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val steps = stringResource(R.string.denied_steps)
    val noPrompt = stringResource(R.string.denied_steps_no_prompt)
    val settings = stringResource(R.string.settings)
    val alwaysAllowAll = stringResource(R.string.denied_steps_always_allow_all)
    Text(
        text = buildAnnotatedString {
            appendTemplate(
                steps,
                { withStyle(strong) { append(noPrompt) } },
                { appendInlineContent("settings", settings) },
                { appendInlineContent("chevron", ">") },
                { withStyle(strong) { append(alwaysAllowAll) } },
            )
        },
        inlineContent = mapOf(
            "settings" to inlineIcon(Icons.Outlined.Settings),
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
