package app.wend.sea.feature.trip

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.WendPrimaryButton
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.component.rememberThumbnail
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.feature.viewer.PickPhoto
import app.wend.sea.feature.viewer.PickViewer
import app.wend.sea.feature.viewer.ViewerAction
import app.wend.sea.feature.viewer.ViewerInk
import app.wend.sea.feature.viewer.ViewerItem
import app.wend.sea.feature.viewer.ViewerWarn
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import coil3.compose.AsyncImage

/**
 * 시안 ⑯ 사진 정리하기: 이유별 머리 + 4열 사진, 전부 체크된 채로 시작하고 남길 사진만 눌러서 뺀다.
 * 아래 버튼은 체크된 장수만큼 "N장 휴지통으로 옮기기" → Android 시스템 확인창 → 허용되면 상세로 돌아가 안내.
 */
@Composable
fun CleanUpScreen(
    onBack: () -> Unit,
    /** 휴지통으로 옮겼다 — 옮긴 장수 */
    onDone: (count: Int) -> Unit,
    viewModel: CleanUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.done.collect { currentOnDone(it) } }

    // 확인창을 띄울 때 넘긴 사진 — 창이 떠 있는 동안 선택이 바뀌지 않으니 그대로 덜어 낸다
    var trashing by remember { mutableStateOf<List<MediaThumbnail>>(emptyList()) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val items = trashing
        trashing = emptyList()
        if (result.resultCode == Activity.RESULT_OK && items.isNotEmpty()) viewModel.onTrashed(items)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.surface)) {
            WendTopAppBar(onBack = onBack, title = { Text("사진 정리하기") })
            if (!uiState.loaded) return@Column
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when {
                            uiState.groups.isNotEmpty() -> "필요 없어 보이는 사진을 골라 뒀어요. 남기고 싶은 사진은 눌러서 빼 주세요."
                            uiState.pending -> "아직 사진을 살펴보는 중이에요. 잠시 뒤에 다시 열어 주세요."
                            uiState.hasBestShots -> "이 여행에는 정리할 만한 사진이 없어요."
                            else -> "이 여행에는 정리할 만한 사진이 없어요. 베스트샷을 고르면 같은 장면의 나머지 사진을 여기서 정리할 수 있어요."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                uiState.groups.forEach { group ->
                    item(key = "head-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                        // 시안 .cl-cat: 14px 위, 4px 아래(그리드 위 8px 은 간격 6dp 와 합쳐 맞춤)
                        Row(
                            Modifier.padding(top = 14.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(group.title, style = GroupHeadStyle, color = colors.onSurface, modifier = Modifier.alignByBaseline())
                            Text(
                                (group.sub?.let { "$it · " } ?: "") + "${group.photos.size}장",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    items(group.photos, key = { "${group.key}-${it.media.mediaId}" }) { photo ->
                        val index = group.photos.indexOf(photo)
                        CleanPhoto(
                            photo = photo.media,
                            checked = photo.media !in uiState.kept,
                            label = "${group.title} ${index + 1}" + (if (photo.place.isNotEmpty()) ", ${photo.place}" else ""),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.toggle(photo.media)
                            },
                            onZoom = { viewModel.view(group.key, index) },
                        )
                    }
                }
            }
            if (uiState.groups.isNotEmpty()) {
                val selected = uiState.selected
                Column(Modifier.background(colors.surface).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    // 시안 .cl-note
                    Row(
                        Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            "휴지통으로 옮긴 사진은 30일 동안 갤러리에서 되살릴 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    WendPrimaryButton(
                        text = if (selected.isNotEmpty()) "${selected.size}장 휴지통으로 옮기기" else "옮길 사진을 골라 주세요",
                        onClick = {
                            // Wend 는 직접 지우지 않는다 — Android 시스템 확인창을 거쳐 휴지통으로만
                            val request = MediaStore.createTrashRequest(context.contentResolver, selected.map { it.uri }, true)
                            trashing = selected
                            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                    )
                }
            }
        }
        // 시안 ⑯ 크게 보기: 같은 이유끼리 넘기며 정리하기 ✓ / 남기기 — 바꾼 것은 목록에 그대로
        uiState.viewing?.let { (key, index) ->
            val group = uiState.groups.firstOrNull { it.key == key } ?: return@let
            PickViewer(
                photos = group.photos.map { PickPhoto(ViewerItem(it.media, it.takenAt), it.place, it.day) },
                startIndex = index,
                best = { false },
                onBack = { viewModel.view(null) },
            ) { item ->
                val checked = item.media !in uiState.kept
                ViewerAction(
                    if (checked) Icons.Rounded.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    if (checked) "정리하기" else "남기기",
                    iconTint = if (checked) ViewerWarn else ViewerInk,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    viewModel.toggle(item.media)
                }
            }
        }
    }
}

/** 시안 `.cl-grid button`: 정사각 12dp 모서리, 오른쪽 위 체크 원(체크되면 빨강), 남길 사진은 옅게 */
@Composable
private fun CleanPhoto(photo: MediaThumbnail, checked: Boolean, label: String, onClick: () -> Unit, onZoom: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (checked) 1f else 0.55f, label = "alpha")
    val mark by animateFloatAsState(if (checked) 1f else 0f, label = "mark")
    val circle by animateColorAsState(if (checked) colors.error else CheckIdle, label = "circle")
    val ring by animateColorAsState(if (checked) colors.error else Color.White, label = "ring")
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
        // 남길 사진은 옅게 — 크게 보기 버튼은 그대로
        Box(
            Modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(colors.surfaceContainer)
                .semantics {
                    contentDescription = label
                    selected = checked
                }
                .clickable(role = Role.Checkbox, onClick = onClick),
        ) {
            AsyncImage(
                model = rememberThumbnail(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .background(circle, CircleShape)
                    .border(BorderStroke(2.dp, ring), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp).alpha(mark))
            }
        }
        ZoomButton("$label 크게 보기", onZoom, Modifier.align(Alignment.BottomStart))
    }
}

private val GroupHeadStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.8.sp, lineHeightStyle = CssLineHeight)

/** 시안 `rgba(0,0,0,.18)` */
private val CheckIdle = Color(0x2E000000)
