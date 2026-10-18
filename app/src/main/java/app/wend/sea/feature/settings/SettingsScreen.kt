package app.wend.sea.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import app.wend.sea.R
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
import app.wend.sea.core.text.appendTemplate
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.BestShotSaveMode
import app.wend.sea.feature.bestshot.BestShotModeDialog
import app.wend.sea.feature.analysis.ReanalyzeDialog
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
                SettingsEvent.SaveFailed -> Toast.makeText(context, R.string.best_save_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        WendTopAppBar(onBack = onBack, title = { Text(stringResource(R.string.settings)) })
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
                    description = stringResource(R.string.settings_globe_description),
                    // 64dp 라 혼자 천천히 돌 때는 한 프레임에 1px 도 안 움직인다 — 초당 15번이면 충분하다(발열)
                    idleFps = 15,
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_name, uiState.nickname), style = NameStyle, color = colors.onSurface)
                    Text(
                        pluralStringResource(R.plurals.count_media, uiState.mediaCount, formatCount(uiState.mediaCount)) + " · " +
                            pluralStringResource(R.plurals.count_trips, uiState.tripCount, formatCount(uiState.tripCount)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = { dialog = NAME }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.change_nickname), tint = colors.onSurfaceVariant)
                }
            }

            Section(stringResource(R.string.settings_section_access))
            SettingRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(R.string.settings_photos_videos),
                sub = stringResource(R.string.settings_photos_videos_sub),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) {
                when (uiState.access) {
                    MediaAccess.All -> State(Icons.Rounded.CheckCircle, stringResource(R.string.settings_all), IncludedGreen)
                    MediaAccess.Partial -> State(Icons.Rounded.CheckCircle, stringResource(R.string.settings_partial), PartialInk)
                    MediaAccess.None -> State(Icons.Rounded.Error, stringResource(R.string.not_allowed), colors.error)
                }
            }
            Divider()
            SettingRow(icon = Icons.Outlined.LocationOn, title = stringResource(R.string.read_photo_location), sub = stringResource(R.string.settings_location_sub)) {
                if (uiState.location) {
                    State(Icons.Rounded.CheckCircle, stringResource(R.string.allowed), IncludedGreen)
                } else {
                    State(Icons.Rounded.Error, stringResource(R.string.not_allowed), colors.error)
                }
            }
            Divider()
            SettingRow(
                icon = Icons.Outlined.PhotoCamera,
                title = stringResource(R.string.settings_location_tag_howto),
                sub = stringResource(R.string.settings_location_tag_sub),
                onClick = { dialog = TAG },
            ) { Chevron() }

            Section(stringResource(R.string.best_save_section))
            SettingRow(
                icon = Icons.Outlined.StarOutline,
                title = stringResource(R.string.best_save_setting),
                sub = stringResource(when (uiState.bestShotSaveMode) {
                    BestShotSaveMode.UNSET -> R.string.best_save_unset
                    BestShotSaveMode.APP_ONLY -> R.string.best_save_app_only
                    BestShotSaveMode.GALLERY -> R.string.best_save_gallery
                }),
                onClick = { dialog = BEST_SAVE },
            ) { Chevron() }
            Text(
                stringResource(R.string.best_save_change_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 58.dp, end = 20.dp, bottom = 4.dp),
            )

            Section(stringResource(R.string.settings_section_find))
            SettingRow(
                icon = Icons.Outlined.TravelExplore,
                title = stringResource(R.string.settings_rescan),
                sub = stringResource(R.string.settings_rescan_sub),
                onClick = { dialog = RESCAN },
            ) { Chevron() }

            Section(stringResource(R.string.settings_section_data))
            SettingRow(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.settings_wipe),
                sub = stringResource(R.string.settings_wipe_sub),
                danger = true,
                onClick = { dialog = WIPE },
            )

            Section(stringResource(R.string.settings_section_about))
            SettingRow(icon = Icons.Outlined.Description, title = stringResource(R.string.open_source_licenses), onClick = onLicenses) { Chevron() }
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
        BEST_SAVE -> BestShotModeDialog(
            current = uiState.bestShotSaveMode,
            onSelect = {
                viewModel.setBestShotSaveMode(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        NAME -> NicknameDialog(current = uiState.nickname, onDismiss = { dialog = null }) {
            viewModel.saveNickname(it)
            dialog = null
        }
        TAG -> LocationTagSheet(onDismiss = { dialog = null })
        RESCAN -> ReanalyzeDialog(
            onConfirm = {
                dialog = null
                viewModel.rescan()
            },
            onDismiss = { dialog = null },
        )
        WIPE -> ConfirmDialog(
            icon = Icons.Outlined.DeleteForever,
            title = stringResource(R.string.settings_wipe_title),
            text = stringResource(R.string.settings_wipe_body),
            confirm = stringResource(R.string.settings_wipe_confirm),
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
        title = { Text(stringResource(R.string.change_nickname)) },
        text = {
            TextField(
                value = value,
                onValueChange = { if (it.text.length <= NicknameUiState.MAX_LENGTH) value = it },
                label = { Text(stringResource(R.string.nickname)) },
                supportingText = {
                    Text(
                        when (state.help) {
                            NicknameHelp.Default -> stringResource(R.string.nickname_help)
                            NicknameHelp.InvalidChars -> stringResource(R.string.nickname_invalid)
                            NicknameHelp.TooShort -> stringResource(R.string.nickname_too_short)
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text(stringResource(R.string.action_cancel)) }
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
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text(stringResource(R.string.action_cancel)) }
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
            Text(stringResource(R.string.settings_location_tag_howto), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step(1, stringResource(R.string.howto_step_1))
                Step(2, stringResource(R.string.howto_step_2))
                val step3 = stringResource(R.string.howto_step_3)
                val locationTag = stringResource(R.string.location_tag)
                Step(3, buildAnnotatedString {
                    appendTemplate(step3, { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(locationTag) } })
                })
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(Bot.Pin, clock, Modifier.size(40.dp))
                BotBubble(stringResource(R.string.howto_bot))
            }
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp, bottom = 8.dp),
            ) { Text(stringResource(R.string.action_got_it)) }
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
private const val BEST_SAVE = "best_save"

/** 시안 #9a2a14 — 섹션 머리·대화상자 글자 버튼(종이색 위 대비 4.5:1 이상) */
private val Accent = Color(0xFF9A2A14)

/** 시안 .state.some */
private val PartialInk = Color(0xFF8A5A00)

private val NameStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.7.sp, lineHeightStyle = CssLineHeight)
private val SectionStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp, letterSpacing = 0.2.sp)
private val RowTitleStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.6.sp)
private val StateStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp)
private val StepNumStyle = TextStyle(fontFamily = IbmPlexSansKr, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 12.sp)
