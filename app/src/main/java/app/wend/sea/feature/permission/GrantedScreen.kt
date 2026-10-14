package app.wend.sea.feature.permission

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.wend.sea.data.MediaAccess
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.ALBUM_DOT_COUNT
import app.wend.sea.core.designsystem.component.ALBUM_FILL_MS
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.rememberReducedMotion
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import java.text.NumberFormat

private val PinTint = Color(0xFFA8301B)

/** 시안 `.res-row .n`: 600 17px/1 */
private val CountStyle = TextStyle(
    fontFamily = IbmPlexSansKr,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 17.sp,
    lineHeightStyle = CssLineHeight,
)

@Composable
fun GrantedScreen(
    onResult: (MediaAccess) -> Unit,
    viewModel: GrantedViewModel = hiltViewModel(),
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()

    // 지구본이 앞쪽부터 채워지는 동안 숫자도 같이 올라간다
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(ALBUM_FILL_MS, easing = LinearEasing))
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.checkAccess()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GrantedEvent.ShowMessage -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    snackbarHostState.showSnackbar(event.message)
                }
                is GrantedEvent.ShowResult -> {
                    haptic.performResultFeedback(event.access)
                    currentOnResult(event.access)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PermBottomBar {
                WendPrimaryButton(
                    text = "여행 찾기 시작",
                    onClick = viewModel::onStartAnalysis,
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
            PermHeading(
                buildAnnotatedString {
                    append("${uiState.nickname}님의 앨범이\n")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("열렸어요") }
                },
                modifier = Modifier.padding(top = 24.dp),
            )
            PermSub("지구본의 방울이 이제 실제 사진이에요.")
            AlbumStage(
                lit = ALBUM_DOT_COUNT,
                height = 268.dp,
                globeSize = 260.dp,
                description = "앨범이 열리면서 지구본의 방울이 앞쪽부터 진하게 채워지는 애니메이션",
                caption = listOf(InkDot to "볼 수 있는 사진"),
            )
            val f = fill.value
            Column(Modifier.padding(top = 4.dp)) {
                CountRow(Icons.Outlined.PhotoLibrary, "사진", (uiState.photos * f).toInt(), "장")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CountRow(Icons.Outlined.Videocam, "영상", (uiState.videos * f).toInt(), "개")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CountRow(Icons.Rounded.LocationOn, "위치가 남아 있는 사진·영상", (uiState.withLocation * f).toInt(), "개", pin = true)
            }
        }
    }
}

/** 시안 `.res-row`: 아이콘 22 · body-l 라벨 · 오른쪽 끝 숫자 */
@Composable
private fun CountRow(icon: ImageVector, label: String, count: Int, unit: String, pin: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (pin) PinTint else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(NumberFormat.getIntegerInstance().format(count) + unit, style = CountStyle)
    }
}
