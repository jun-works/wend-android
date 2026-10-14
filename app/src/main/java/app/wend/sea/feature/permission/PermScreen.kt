package app.wend.sea.feature.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.data.MediaAccess

// 시안 .why-row .ic.pin
private val PinTint = Color(0xFFA8301B)
private val PinTintBg = Color(0xFFFBE1D9)

@Composable
fun PermScreen(
    onBack: () -> Unit,
    onResult: (MediaAccess) -> Unit,
    viewModel: PermViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnResult by rememberUpdatedState(onResult)
    val haptic = LocalHapticFeedback.current
    val permission = rememberMediaPermissionRequest(
        onResult = viewModel::onRequestResult,
        onReturnFromSettings = viewModel::onReturnFromSettings,
    )

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
        topBar = { WendTopAppBar(onBack = onBack) },
        bottomBar = {
            PermBottomBar {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("다음 안내창에서 ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) { append("모두 허용") }
                            append("을 골라 주세요")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WendPrimaryButton(
                    text = "사진 접근 허용하기",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        // 두 번 거절하면 Android 가 안내창을 더 띄우지 않으므로 설정으로 보낸다
                        if (uiState.settingsOnly) permission.openSettings() else permission.request()
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            PermHeading(
                AnnotatedString("${uiState.nickname}님의 앨범을\n열어 주세요"),
                modifier = Modifier.padding(top = 4.dp),
            )
            PermSub("지금은 사진이 보이지 않아 지구본이 비어 있어요.")
            AlbumStage(
                lit = 0,
                height = 268.dp,
                globeSize = 260.dp,
                description = "아직 열리지 않은 앨범을 뜻하는 옅은 색 지구본",
            )
            Column(Modifier.padding(top = 8.dp)) {
                WhyRow(Icons.Outlined.PhotoLibrary, "사진·영상 전체 보기", "여행은 사진이 며칠씩 이어져야 찾을 수 있어서, 일부만 보면 여행이 잘려요.")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WhyRow(Icons.Rounded.LocationOn, "사진 속 위치 읽기", "핀이 장소를 알아낼 때 써요. 사진 권한과 함께 요청돼요.", pin = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WhyRow(Icons.Outlined.PhonelinkLock, "기기 밖으로 보내지 않아요", "어디에도 올리지 않고 이 휴대폰 안에서만 읽어요. 직접 고르기 전에는 지우지 않아요.")
            }
        }
    }
}

/** 시안 `.why-row`: 40dp 원형 아이콘 + 굵은 제목(600 15/1.35) + 설명(body-m) */
@Composable
private fun WhyRow(icon: ImageVector, title: String, description: String, pin: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (pin) PinTintBg else colors.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (pin) PinTint else colors.onSurface, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.25.sp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 결과에 맞는 햅틱: 모두 허용 Confirm · 일부 틱 · 거절 Reject */
internal fun androidx.compose.ui.hapticfeedback.HapticFeedback.performResultFeedback(access: MediaAccess) {
    performHapticFeedback(
        when (access) {
            MediaAccess.All -> HapticFeedbackType.Confirm
            MediaAccess.Partial -> HapticFeedbackType.SegmentTick
            MediaAccess.None -> HapticFeedbackType.Reject
        },
    )
}
