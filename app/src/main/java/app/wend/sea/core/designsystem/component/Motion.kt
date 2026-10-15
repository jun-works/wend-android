package app.wend.sea.core.designsystem.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 시스템 애니메이션 배율이 0 이면 계속 도는 애니메이션을 멈춘 상태로 보여준다. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 장식용 무한 애니메이션이 공유하는 저주사율 시계(경과 초).
 * 120Hz 화면에서 요소마다 따로 애니메이션을 돌리면 매 vsync 마다 그려져 발열이 커지므로,
 * 한 화면의 장식 애니메이션은 이 시계 하나를 그리기 단계에서만 읽는다.
 * 애니메이션 끄기 설정이면 0 에 멈춰 있다.
 */
@Composable
fun rememberAmbientClock(fps: Int = 30, active: Boolean = true): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    val reduced = rememberReducedMotion()
    // 멈추면(active = false) 그 시각에 멈췄다가 다시 이어 간다
    LaunchedEffect(reduced, fps, active) {
        if (reduced || !active) return@LaunchedEffect
        // 그 사이에는 화면 갱신(120Hz)마다 깨어나 확인하지 않고 쉰다 — 깨어나는 것만으로도 CPU 를 쓴다
        val sleepMs = (1000L / fps - 4).coerceAtLeast(1)
        var start = 0L
        val from = clock.floatValue
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                clock.floatValue = from + (now - start) / 1e9f
            }
            delay(sleepMs)
        }
    }
    return clock
}
