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
                    text = if (settingsOnly) "설정에서 허용하기" else "다시 허용하기",
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
            PermHeading(AnnotatedString("앨범이 닫혀 있어서\n여행을 찾을 수 없어요"), modifier = Modifier.padding(top = 24.dp))
            AlbumStage(
                lit = 0,
                height = 236.dp,
                globeSize = 220.dp,
                description = "방울이 모두 옅은 색으로 남아 있는 빈 지구본",
            )
            // 시안 .dn-say: 온보딩의 핀이 다시 나와 안심시킨다
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
                        if (settingsOnly) {
                            "두 번 거절하시면 Android가 안내창을 더 띄우지 않아요. 설정에서 한 번만 바꿔 주시면 돼요."
                        } else {
                            "읽을 사진이 없으면 틱이랑 저는 할 수 있는 게 없어요. 사진은 어디에도 올리지 않고, 직접 고르기 전에는 지우지 않아요."
                        },
                    )
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
    Text(
        text = buildAnnotatedString {
            withStyle(strong) { append("안내창이 더는 뜨지 않아요.") }
            append(" 설정에서 직접 바꿔 주세요.\n")
            appendInlineContent("settings", "설정")
            append(" 권한 ")
            appendInlineContent("chevron", ">")
            append(" 사진 및 동영상 ")
            appendInlineContent("chevron", ">")
            append(" ")
            withStyle(strong) { append("항상 모두 허용") }
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
