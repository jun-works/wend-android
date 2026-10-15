package app.wend.sea

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wend.sea.data.geo.CityDatabase
import app.wend.sea.data.geo.CityFinder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * 실제 assets/geo/cities.db 로 Room 스키마 확인 + 조회 시간 측정.
 * 앱의 cities.db 를 건드리지 않게 다른 파일 이름으로 복사해서 잰다.
 */
@RunWith(AndroidJUnit4::class)
class CityFinderTimingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun open() = Room.databaseBuilder(context, CityDatabase::class.java, NAME)
        .createFromAsset("geo/cities.db")
        .build()

    private fun ms(start: Long) = (SystemClock.elapsedRealtimeNanos() - start) / 1e6

    @Test
    fun firstAndLaterLookups(): Unit = runBlocking {
        context.deleteDatabase(NAME)
        val points = listOf(34.985 to 135.758, 16.054 to 108.202, 37.5665 to 126.978, 48.8566 to 2.3522, 0.0 to -160.0, 40.7128 to -74.006)

        // 1) 설치 후 처음: 에셋 복사 + 스키마 확인 + 조회
        var db = open()
        var finder = CityFinder(db.cityDao())
        var t = SystemClock.elapsedRealtimeNanos()
        val kyoto = finder.nearestCity(34.985, 135.758, Locale.KOREA)
        val firstEver = ms(t)
        assertEquals("교토", kyoto?.name)
        assertEquals("일본", kyoto?.countryName)

        // 2) 이후 조회
        val later = points.flatMap { (lat, lon) ->
            List(20) {
                t = SystemClock.elapsedRealtimeNanos()
                finder.nearestCity(lat, lon, Locale.KOREA)
                ms(t)
            }
        }.sorted()
        assertNull(finder.nearestCity(0.0, -160.0, Locale.KOREA))
        assertEquals("峴港", finder.nearestCity(16.068, 108.221, Locale.TRADITIONAL_CHINESE)?.name)
        db.close()

        // 3) 앱을 다시 켰을 때 처음(파일은 이미 복사됨)
        db = open()
        finder = CityFinder(db.cityDao())
        t = SystemClock.elapsedRealtimeNanos()
        finder.nearestCity(48.8566, 2.3522, Locale.KOREA)
        val firstAfterRestart = ms(t)
        db.close()
        context.deleteDatabase(NAME)

        Log.i(
            TAG,
            "device=${android.os.Build.MODEL} firstEver=%.1fms firstAfterRestart=%.1fms later median=%.2fms p95=%.2fms max=%.2fms (n=%d)".format(
                firstEver, firstAfterRestart, later[later.size / 2], later[(later.size * .95).toInt()], later.last(), later.size,
            ),
        )
    }

    private companion object {
        const val NAME = "cities-timing.db"
        const val TAG = "CityTiming"
    }
}
