package app.wend.sea.feature.trip

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage

/**
 * 시안 ⑮ 베스트샷 고르기: 장소 머리 아래 같은 장면마다 한 줄, 장면마다 한 장을 고르거나 고르지 않는다.
 * 앱은 추천하지 않는다 — 처음엔 아무것도 골라져 있지 않다(이미 고른 베스트샷만 고른 채로).
 * 고른 사진은 살짝 떠오르고 노란 테두리 + 별. 정하기를 눌러야 저장되고, 뒤로 가면 버린다.
 */
@Composable
fun BestShotScreen(
    onBack: () -> Unit,
    /** 정했다 — 고른 장수 */
    onDone: (count: Int) -> Unit,
    viewModel: BestShotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.done.collect { currentOnDone(it) } }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        WendTopAppBar(onBack = onBack, title = { Text("베스트샷 고르기") })
        if (!uiState.loaded) return@Column
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
            item(key = "intro") {
                Text(
                    when {
                        uiState.places.isNotEmpty() ->
                            "같은 장면을 여러 번 찍은 사진끼리 모았어요. 장면마다 마음에 드는 한 장을 골라 주세요. 안 골라도 돼요. 고른 사진은 여행의 대표 사진이 돼요."
                        uiState.pending -> "아직 사진을 살펴보는 중이에요. 잠시 뒤에 다시 열어 주세요."
                        else -> "이 여행에는 같은 장면을 여러 번 찍은 사진이 없어요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            uiState.places.forEach { place ->
                item(key = "place-${place.index}") {
                    Text(
                        "${place.number}. ${place.name}",
                        style = PlaceHeadStyle,
                        color = colors.onSurface,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                    )
                }
                items(place.scenes, key = { it.key }) { scene ->
                    Column {
                        Text(
                            "${time(scene.start)} · 비슷한 사진 ${scene.photos.size}장",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            items(scene.photos, key = { it.mediaId }) { photo ->
                                val index = scene.photos.indexOf(photo)
                                ScenePhoto(
                                    photo = photo,
                                    picked = uiState.picks[scene.key] == photo,
                                    label = "${place.name} ${time(scene.start)} 비슷한 사진 ${index + 1}",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        viewModel.pick(scene.key, photo)
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "gap-${place.index}") { Box(Modifier.padding(bottom = 6.dp)) }
            }
        }
        if (uiState.places.isNotEmpty()) {
            val count = uiState.picks.size
            Box(Modifier.background(colors.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                WendPrimaryButton(
                    text = if (count > 0) "${count}장으로 정하기" else "사진을 골라 주세요",
                    onClick = viewModel::save,
                    enabled = count > 0,
                )
            }
        }
    }
}

/** 시안 `.bs-row button`: 104dp 정사각, 고르지 않은 사진은 살짝 바래게, 고른 사진은 떠오르고 노란 테두리 + 별 */
@Composable
private fun ScenePhoto(photo: MediaThumbnail, picked: Boolean, label: String, onClick: () -> Unit) {
    // cubic-bezier(.3,1.5,.5,1) 처럼 살짝 넘쳤다 돌아오는 움직임
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val lift by animateDpAsState(if (picked) (-4).dp else 0.dp, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "lift")
    val scale by animateFloatAsState(if (picked) 1.04f else 1f, bouncy, label = "scale")
    val star by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "star")
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .offset(y = lift)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (picked) Modifier.shadow(8.dp, shape, ambientColor = ShadowInk, spotColor = ShadowInk) else Modifier)
            .size(104.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (picked) Modifier.border(BorderStroke(3.dp, StarGold), shape) else Modifier)
            .semantics {
                contentDescription = label
                selected = picked
            }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(
            model = rememberThumbnail(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (picked) null else Faded,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp)
                .graphicsLayer { scaleX = star; scaleY = star }
                .background(StarGold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = StarInk, modifier = Modifier.size(17.dp))
        }
    }
}

private val PlaceHeadStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.8.sp, lineHeightStyle = CssLineHeight)
internal val StarGold = Color(0xFFF2B33D)
private val StarInk = Color(0xFF3A2A06)
private val ShadowInk = Color(0x801F1A16)

/** 시안 `filter:saturate(.75) brightness(.97)` */
private val Faded = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0.75f) }.also { m ->
        val b = ColorMatrix(floatArrayOf(.97f, 0f, 0f, 0f, 0f, 0f, .97f, 0f, 0f, 0f, 0f, 0f, .97f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        m.timesAssign(b)
    },
)
