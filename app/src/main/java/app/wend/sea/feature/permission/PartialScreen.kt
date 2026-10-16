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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.paperCard
import app.wend.sea.core.text.appendTemplate
import app.wend.sea.data.MediaAccess
import app.wend.sea.feature.analysis.photoCount
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
                    text = stringResource(R.string.allow_all_photos),
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
                    Text(stringResource(R.string.partial_start_with_picked), style = MaterialTheme.typography.labelLarge)
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
            val primary = MaterialTheme.colorScheme.primary
            val title = stringResource(R.string.partial_title)
            val pickedText = photoCount(picked)
            PermHeading(
                buildAnnotatedString {
                    appendTemplate(title, { withStyle(SpanStyle(color = primary)) { append(pickedText) } })
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            AlbumStage(
                lit = PARTIAL_LIT_DOTS,
                height = 248.dp,
                globeSize = 240.dp,
                description = stringResource(R.string.partial_globe_description),
                caption = listOf(InkDot to stringResource(R.string.partial_caption_picked), GhostDot to stringResource(R.string.partial_caption_hidden)),
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
                stringResource(R.string.partial_cut_title),
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
            listOf(R.string.partial_cut_start, R.string.partial_cut_trip, R.string.partial_cut_end).map { stringResource(it) }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        val body = stringResource(R.string.partial_cut_body)
        val bodyEm = stringResource(R.string.partial_cut_body_em)
        Text(
            text = buildAnnotatedString {
                appendTemplate(body, { withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(bodyEm) } })
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
