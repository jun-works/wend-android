package app.wend.sea.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.ALBUM_DOT_COUNT
import app.wend.sea.core.designsystem.component.AlbumGlobe
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.designsystem.component.BotAvatar
import app.wend.sea.core.designsystem.component.BotBubble
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.component.rememberAmbientClock
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.core.designsystem.theme.IncludedGreen
import app.wend.sea.data.MediaAccess
import app.wend.sea.feature.analysis.formatCount
import app.wend.sea.feature.nickname.NicknameHelp
import app.wend.sea.feature.nickname.NicknameUiState
import app.wend.sea.feature.permission.PARTIAL_LIT_DOTS

/**
 * 시안 ⑲ 설정. 그림은 이름 옆 작은 앨범 지구본 하나 — 사진 접근 상태만큼 채워진다(모두 = 가득, 일부 = 띄엄띄엄, 없음 = 빈 지구본).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** 처음부터 다시 찾기를 시작했다 — 분석 화면으로 */
    onRescan: () -> Unit,
    /** 데이터를 지웠다 — 처음 화면으로 */
    onWiped: () -> Unit,
    onLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val currentOnRescan by rememberUpdatedState(onRescan)
    val currentOnWiped by rememberUpdatedState(onWiped)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                SettingsEvent.Rescan -> currentOnRescan()
                SettingsEvent.Wiped -> currentOnWiped()
            }
        }
    }

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        WendTopAppBar(onBack = onBack, title = { Text("설정") })
        if (!uiState.loaded) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 4.dp, bottom = 32.dp)) {
            // 시안 .pf-me
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .background(colors.surfaceContainerLow, RoundedCornerShape(20.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AlbumGlobe(
                    lit = when (uiState.access) {
                        MediaAccess.All -> ALBUM_DOT_COUNT
                        MediaAccess.Partial -> PARTIAL_LIT_DOTS
                        MediaAccess.None -> 0
                    },
                    size = 64.dp,
                    description = "앨범 지구본. 볼 수 있는 사진만큼 진하게 채워져 있어요",
                )
                Column(Modifier.weight(1f)) {
                    Text("${uiState.nickname}님", style = NameStyle, color = colors.onSurface)
                    Text(
                        "사진·영상 ${formatCount(uiState.mediaCount)}개 · 여행 ${uiState.tripCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = { dialog = NAME }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "닉네임 바꾸기", tint = colors.onSurfaceVariant)
                }
            }

            Section("사진 접근")
            SettingRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "사진 및 동영상",
                sub = "Android 설정에서 바꿀 수 있어요",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) {
                when (uiState.access) {
                    MediaAccess.All -> State(Icons.Rounded.CheckCircle, "모두 허용", IncludedGreen)
                    MediaAccess.Partial -> State(Icons.Rounded.CheckCircle, "일부 허용", PartialInk)
                    MediaAccess.None -> State(Icons.Rounded.Error, "허용 안 함", colors.error)
                }
            }
            Divider()
            SettingRow(icon = Icons.Outlined.LocationOn, title = "사진 속 위치 읽기", sub = "사진 권한과 함께 허용돼요") {
                if (uiState.location) {
                    State(Icons.Rounded.CheckCircle, "허용", IncludedGreen)
                } else {
                    State(Icons.Rounded.Error, "허용 안 함", colors.error)
                }
            }
            Divider()
            SettingRow(
                icon = Icons.Outlined.PhotoCamera,
                title = "카메라 위치 태그 켜는 법",
                sub = "켜 두면 여행을 더 정확하게 찾아요",
                onClick = { dialog = TAG },
            ) { Chevron() }

            Section("여행 찾기")
            SettingRow(
                icon = Icons.Outlined.TravelExplore,
                title = "처음부터 다시 찾기",
                sub = "직접 고친 내용은 모두 처음으로 돌아가요",
                onClick = { dialog = RESCAN },
            ) { Chevron() }

            Section("데이터")
            SettingRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Wend 데이터 지우기",
                sub = "찾은 여행과 정리한 기록을 지워요. 사진은 그대로 있어요",
                danger = true,
                onClick = { dialog = WIPE },
            )

            Section("정보")
            SettingRow(icon = Icons.Outlined.Description, title = "오픈소스 라이선스", onClick = onLicenses) { Chevron() }
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
            Text(
                "Wend $version",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(20.dp).align(Alignment.CenterHorizontally),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    when (dialog) {
        NAME -> NicknameDialog(current = uiState.nickname, onDismiss = { dialog = null }) {
            viewModel.saveNickname(it)
            dialog = null
        }
        TAG -> LocationTagSheet(onDismiss = { dialog = null })
        RESCAN -> ConfirmDialog(
            icon = Icons.Outlined.TravelExplore,
            title = "처음부터 다시 찾을까요?",
            text = "모든 사진을 다시 읽어서 여행을 찾아요. 직접 넣고 뺀 사진, 만든 여행, 베스트샷, 바꾼 이름이 모두 처음으로 돌아가요.",
            confirm = "다시 찾기",
            confirmColor = Accent,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            viewModel.rescan()
        }
        WIPE -> ConfirmDialog(
            icon = Icons.Outlined.DeleteForever,
            title = "Wend 데이터를 지울까요?",
            text = "찾은 여행, 베스트샷, 직접 넣고 뺀 기록, 닉네임이 지워지고 처음 화면으로 돌아가요. **기기의 사진은 하나도 지워지지 않아요.**",
            confirm = "지우기",
            confirmColor = colors.error,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            viewModel.wipe()
        }
    }
}

/** 시안 .pf-sec: 진한 코랄 600 13 */
@Composable
private fun Section(title: String) {
    Text(title, style = SectionStyle, color = Accent, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 58.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** 시안 .pf-row: 아이콘 · 제목/설명 · 오른쪽(상태·화살표), 최소 64dp */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) colors.error else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitleStyle, color = if (danger) colors.error else colors.onSurface)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

/** 시안 .pf-row .state */
@Composable
private fun State(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = StateStyle, color = color)
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 시안 #prefs-name: 온보딩과 같은 규칙(2~10자, 한글·영문·숫자) */
@Composable
private fun NicknameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    var submitted by remember { mutableStateOf(false) }
    val state = NicknameUiState(value.text, submitted)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("닉네임 바꾸기") },
        text = {
            TextField(
                value = value,
                onValueChange = { if (it.text.length <= NicknameUiState.MAX_LENGTH) value = it },
                label = { Text("닉네임") },
                supportingText = {
                    Text(
                        when (state.help) {
                            NicknameHelp.Default -> "2~10자, 한글·영문·숫자"
                            NicknameHelp.InvalidChars -> "한글·영문·숫자만 쓸 수 있어요"
                            NicknameHelp.TooShort -> "두 글자 이상 입력해 주세요"
                        },
                    )
                },
                isError = state.isError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (state.isValid) onSave(value.text.trim()) else submitted = true },
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text("취소") }
        },
    )
}

/** 시안 #prefs-rescan · #prefs-wipe. [text] 의 `**굵게**` 는 굵은 글씨로 */
@Composable
private fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirm: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Text(
                buildAnnotatedString {
                    text.split("**").forEachIndexed { i, part ->
                        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = confirmColor)) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text("취소") }
        },
    )
}

/** 시안 #tag-sheet: 3단계 + 핀 말풍선 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationTagSheet(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clock = rememberAmbientClock()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("카메라 위치 태그 켜는 법", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step(1, "카메라 앱을 열어요")
                Step(2, "왼쪽 위 설정(⚙)을 눌러요")
                Step(3, buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("위치 태그") }
                    append("를 켜요")
                })
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(Bot.Pin, clock, Modifier.size(40.dp))
                BotBubble("켜 둔 뒤에 찍은 사진부터 장소가 남아요. 기기마다 메뉴 이름이 조금 다를 수 있어요.")
            }
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 8.dp),
            ) { Text("알겠어요") }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) = Step(n, buildAnnotatedString { append(text) })

@Composable
private fun Step(n: Int, text: androidx.compose.ui.text.AnnotatedString) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).background(colors.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = StepNumStyle, color = colors.onSurface)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

private const val NAME = "name"
private const val TAG = "tag"
private const val RESCAN = "rescan"
private const val WIPE = "wipe"

/** 시안 #9a2a14 — 섹션 머리·대화상자 글자 버튼(종이색 위 대비 4.5:1 이상) */
private val Accent = Color(0xFF9A2A14)

/** 시안 .state.some */
private val PartialInk = Color(0xFF8A5A00)

private val NameStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.7.sp, lineHeightStyle = CssLineHeight)
private val SectionStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp, letterSpacing = 0.2.sp)
private val RowTitleStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.6.sp)
private val StateStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp)
private val StepNumStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 12.sp)
