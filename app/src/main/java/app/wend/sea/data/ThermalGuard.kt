package app.wend.sea.data

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 오래 걸리는 파일 읽기 중간에 기기 온도를 보고 쉬어 간다.
 * 보통(MODERATE) 이상이면 한 장마다 잠깐 쉬고, 심함(SEVERE) 이상이면 식을 때까지 멈춘다.
 */
@Singleton
class ThermalGuard @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val power = context.getSystemService(PowerManager::class.java)

    suspend fun pace() {
        while (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) delay(COOL_DOWN_MS)
        if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) delay(BREATHER_MS)
    }

    private companion object {
        const val COOL_DOWN_MS = 5_000L
        const val BREATHER_MS = 60L
    }
}
