package app.wend.sea.feature.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.paperCard
import app.wend.sea.data.MediaAccess
import app.wend.sea.feature.analysis.rememberStartWithNotificationPermission

private val PinTint = Color(0xFFA8301B)
private val StripOff = Color(0xFFE2D9CB)

// 잘린 여행 예시: 12일 중 3일만 고른 모습 — 시안 고정 예시
private val StripPicked = setOf(1, 5, 8)

@Composable
fun PartialScreen(
    onResult: (MediaAccess) -> Unit,
    onAnalysisStarted: () -> Unit,
    viewModel: PartialViewModel = hiltViewModel(),
) {
    val currentOnStarted by rememberUpdatedState(onAnalysisStarted)
    val start = rememberStartWithNotificationPermission {
        viewModel.startAnalysis()
        currentOnStarted()
    }
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val currentOnResult by rememberUpdatedState(onResult)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.refresh() },
    )

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PartialEvent.ShowResult -> {
                    haptic.performResultFeedback(event.access)
                    currentOnResult(event.access)
                }
                is PartialEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                PartialEvent.ReadyToStart -> start()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PermBottomBar {
                WendPrimaryButton(
                    text = "모든 사진 허용하기",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        // 재요청 안내창에서 모두 허용을 한 번 안 고르면 Android 가 그 뒤로는 사진 고르는 화면만 띄운다.
                        // 그때는 모두 허용을 고를 수 있는 앱 권한 설정으로 보낸다.
                        if (permission.canShowDialog()) permission.request() else permission.openSettings()
                    },
                )
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.onStartWithPicked()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    Text("고른 사진으로 시작할게요", style = MaterialTheme.typography.labelLarge)
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
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            PermHeading(
                buildAnnotatedString {
                    append("고르신 ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("${picked}장") }
                    append("만\n볼 수 있어요")
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            AlbumStage(
                lit = PARTIAL_LIT_DOTS,
                height = 248.dp,
                globeSize = 240.dp,
                description = "지구본 대부분이 옅은 채로, 고른 사진만큼만 진한 방울이 띄엄띄엄 보이는 모습",
                caption = listOf(InkDot to "고른 사진", GhostDot to "안 보이는 사진"),
            )
            CutTripCard(Modifier.padding(top = 12.dp))
        }
    }
}

/** 시안 `.cut`: 12일짜리 여행에서 3장만 고르면 하루짜리 여행 셋으로 잘린다는 예시 */
@Composable
private fun CutTripCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .paperCard(RoundedCornerShape(20.dp), colors.surfaceContainerLowest)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = PinTint, modifier = Modifier.size(18.dp))
            Text(
                "이렇게 여행이 잘릴 수 있어요",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.3f),
            )
        }
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(12) { day ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .paperCard(RoundedCornerShape(6.dp), if (day in StripPicked) InkDot else StripOff, underline = false),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("7월 12일", "제주 · 12일 동안 41장", "7월 23일").forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Text(
            text = buildAnnotatedString {
                append("3장만 고르면 틱과 핀은 ")
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append("하루짜리 여행 세 개") }
                append("로 읽어요. 사진은 나중에 더 고를 수도 있어요.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
