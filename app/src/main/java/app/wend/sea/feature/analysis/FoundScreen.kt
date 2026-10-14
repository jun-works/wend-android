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
import androidx.compose.ui.Modifier
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
import app.wend.sea.feature.permission.PermBottomBar
import app.wend.sea.feature.permission.PermHeading
import app.wend.sea.feature.permission.rememberMediaPermissionRequest

@Composable
fun FoundScreen(
    onReanalyze: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    val partial = summary?.partial == true
    val currentOnReanalyze by rememberUpdatedState(onReanalyze)
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.checkAccess() },
    )

    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
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
                WendPrimaryButton(
                    text = "여행 보러 가기",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.onBrowse()
                    },
                )
                if (partial) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (permission.canShowDialog()) permission.request() else permission.openSettings()
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) { Text("모든 사진 허용하기", style = MaterialTheme.typography.labelLarge) }
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
            val trips = uiState.trips
            PermHeading(
                buildAnnotatedString {
                    append(if (partial) "고르신 사진에서\n" else "${uiState.nickname}님의 ")
                    append("여행 ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("${trips.size}개") }
                    append(if (partial) "를 찾았어요" else "를\n찾았어요")
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            if (summary != null) {
                val strong = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildAnnotatedString {
                        if (partial) {
                            append("고르신 ${formatCount(summary.total)}장 중 ")
                            withStyle(strong) { append("${formatCount(summary.included)}장") }
                            append("이 여행에 담겼어요.")
                        } else {
                            append("사진·영상 ${formatCount(summary.total)}개 중 ")
                            withStyle(strong) { append("${formatCount(summary.included)}개") }
                            append("가 여행에 담겼어요. 나머지는 앨범에 그대로 있어요.")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            TripStage(
                trips = trips,
                partial = partial,
                progress = { 1f },
                height = 224.dp,
                globeSize = 212.dp,
                circle = 194.dp,
                description = "여행마다 다른 색 덩어리로 뭉친 지구본",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trips.reversed().take(3).forEach { TripTicket(it) }
            }
            if (trips.size > 3) {
                Text(
                    "그 밖에 여행 ${trips.size - 3}개가 더 있어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
            if (partial) {
                // 시안 .fd-cut: 일부만 보면 여행이 잘려 보일 수 있다는 안내
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "고른 사진만 보면 한 여행이 여러 개로 나뉘어 보일 수 있어요. 모든 사진을 허용하면 이어서 찾아 드려요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
