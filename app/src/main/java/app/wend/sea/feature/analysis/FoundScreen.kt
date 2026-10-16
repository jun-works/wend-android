package app.wend.sea.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.pluralStringResource
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
import app.wend.sea.core.text.appendTemplate
import app.wend.sea.feature.permission.PermBottomBar
import app.wend.sea.feature.permission.PermHeading
import app.wend.sea.feature.permission.rememberMediaPermissionRequest

@Composable
fun FoundScreen(
    onReanalyze: () -> Unit,
    onOpenTrips: () -> Unit,
    onTripClick: (number: Int) -> Unit,
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
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = { viewModel.checkAccess(fromUser = true) },
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
                ResultEvent.OpenTrips -> currentOnOpenTrips()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PermBottomBar {
                WendPrimaryButton(
                    text = stringResource(R.string.found_browse),
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
                    ) { Text(stringResource(R.string.allow_all_photos), style = MaterialTheme.typography.labelLarge) }
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
            val primary = MaterialTheme.colorScheme.primary
            val title = stringResource(if (partial) R.string.found_title_partial else R.string.found_title)
            val tripCount = pluralStringResource(R.plurals.found_trip_count, trips.size, trips.size)
            PermHeading(
                buildAnnotatedString {
                    appendTemplate(
                        title,
                        { append(uiState.nickname) },
                        { withStyle(SpanStyle(color = primary)) { append(tripCount) } },
                    )
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            if (summary != null) {
                val strong = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                val text = pluralStringResource(if (partial) R.plurals.found_summary_partial else R.plurals.found_summary, summary.total)
                val included = stringResource(
                    if (partial) R.string.count_photos_short else R.string.count_items_short,
                    formatCount(summary.included),
                )
                Text(
                    text = buildAnnotatedString {
                        appendTemplate(text, { append(formatCount(summary.total)) }, { withStyle(strong) { append(included) } })
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
                description = stringResource(R.string.found_globe_description),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trips.reversed().take(3).forEach { trip ->
                    TripTicket(
                        trip,
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onTripClick(trip.number)
                        },
                    )
                }
            }
            if (trips.size > 3) {
                Text(
                    pluralStringResource(R.plurals.found_more, trips.size - 3, trips.size - 3),
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
                        stringResource(R.string.found_partial_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
