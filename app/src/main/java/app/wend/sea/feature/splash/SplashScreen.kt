package app.wend.sea.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.gooey
import app.wend.sea.core.designsystem.theme.OrbColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BLOB_DURATION_MS = 2400
private const val AUTO_ADVANCE_MS = 3200L
private val BlobEasing = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)

/** 한 키프레임: 진행 위치(0~1), 중심 기준 이동(dp), 배율 */
private data class Key(val at: Float, val x: Float, val y: Float, val scale: Float)

private class Blob(val color: Color, val diameter: Float, val keys: List<Key>)

// 흩어진 방울 3개가 모여 붙고, 작은 민트 방울이 튀어나온다.
private val Blobs = listOf(
    Blob(OrbColors.Coral, 76f, listOf(Key(0f, -62f, 30f, .5f), Key(.55f, -14f, 6f, 1f), Key(1f, -30f, 12f, .86f))),
    Blob(OrbColors.Sun, 76f, listOf(Key(0f, 58f, 40f, .5f), Key(.55f, 18f, 4f, .9f), Key(1f, 30f, 16f, .74f))),
    Blob(OrbColors.Sea, 76f, listOf(Key(0f, 0f, -70f, .5f), Key(.55f, 2f, -18f, .95f), Key(1f, 4f, -30f, .8f))),
    Blob(OrbColors.Mint, 30f, listOf(Key(0f, 0f, 0f, 0f), Key(.6f, 0f, 0f, 0f), Key(1f, 52f, -46f, 1f))),
)

private fun Blob.at(progress: Float): Key {
    val i = keys.indexOfLast { it.at <= progress }.coerceIn(0, keys.size - 2)
    val a = keys[i]
    val b = keys[i + 1]
    val t = BlobEasing.transform(((progress - a.at) / (b.at - a.at)).coerceIn(0f, 1f))
    fun lerp(from: Float, to: Float) = from + (to - from) * t
    return Key(progress, lerp(a.x, b.x), lerp(a.y, b.y), lerp(a.scale, b.scale))
}

@Composable
fun SplashScreen(
    onFinished: (SplashDestination) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val onFinishedState by rememberUpdatedState(onFinished)
    var requested by remember { mutableStateOf(false) }
    // 애니메이션이 끝났거나 화면을 눌렀고, 저장된 닉네임 여부도 읽었을 때 한 번만 이동
    LaunchedEffect(requested, destination) {
        val target = destination
        if (requested && target != null) onFinishedState(target)
    }
    val finish = { requested = true }
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val word = remember { Animatable(0f) }
    val foot = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { progress.animateTo(1f, tween(BLOB_DURATION_MS, easing = LinearEasing)) }
        launch {
            // 방울 셋이 붙는 순간 · 민트 방울이 튀어나오는 순간
            delay((BLOB_DURATION_MS * .55f).toLong())
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay((BLOB_DURATION_MS * .45f).toLong())
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        launch { word.animateTo(1f, tween(800, delayMillis = 1300)) }
        launch { foot.animateTo(1f, tween(600, delayMillis = 1800)) }
        delay(AUTO_ADVANCE_MS)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.splash_start),
                onClick = { finish() },
            )
            // 시안처럼 상태바·내비게이션 바를 뺀 영역 기준으로 가운데 정렬
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .gooey(blurSigma = 6.dp, keepColors = true),
            ) {
                val p = progress.value
                Blobs.forEach { blob ->
                    val k = blob.at(p)
                    drawCircle(
                        color = blob.color,
                        radius = blob.diameter.dp.toPx() / 2f * k.scale,
                        center = center + Offset(k.x.dp.toPx(), k.y.dp.toPx()),
                    )
                }
            }
            Column(
                modifier = Modifier.fadeUp(word.value),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Wend", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.splash_tap),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .fadeUp(foot.value),
        )
    }
}

private fun Modifier.fadeUp(fraction: Float) = graphicsLayer {
    alpha = fraction
    translationY = (1f - fraction) * 10.dp.toPx()
}
