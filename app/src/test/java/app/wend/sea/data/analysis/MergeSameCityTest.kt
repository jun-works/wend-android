package app.wend.sea.data.analysis

import app.wend.sea.data.analysis.TripDetector.Companion.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class MergeSameCityTest {
    private val zone = ZoneOffset.UTC
    private var nextId = 0L

    /** [hour] 시에 [lat] 에서 찍은 사진 [count] 장으로 된 장소 */
    private fun place(hour: Long, lat: Double?, count: Int = 2) = Place(
        media = List(count) { MediaPoint(nextId++, false, hour * HOUR + it * 60_000L, lat, lat?.let { 100.0 }) },
        latitude = lat,
        longitude = lat?.let { 100.0 },
        sameAs = null,
    )

    @Test
    fun `같은 날 이어진 같은 도시는 한 장소`() {
        val result = mergeSameCity(listOf(place(1, 10.0), place(4, 10.2), place(8, 11.0)), listOf(1L, 1L, 2L), listOf("존모드", "존모드", "울란바타르"), zone)
        assertEquals(listOf("존모드", "울란바타르"), result.names)
        assertEquals(4, result.places[0].media.size)
        assertEquals(10.1, result.places[0].latitude!!, 1e-9)
    }

    @Test
    fun `날이 바뀌면 같은 도시라도 나눈다`() {
        val result = mergeSameCity(listOf(place(20, 10.0), place(30, 10.0)), listOf(5L, 5L), listOf("Bulgan", "Bulgan"), zone)
        assertEquals(2, result.places.size)
        // 같은 도시이므로 지도 번호는 앞 장소 하나
        assertEquals(0, result.places[1].sameAs)
    }

    @Test
    fun `다시 들른 도시는 앞 장소 번호`() {
        val result = mergeSameCity(listOf(place(1, 10.0), place(3, 11.0), place(5, 10.0)), listOf(1L, 2L, 1L), listOf("A", "B", "A"), zone)
        assertNull(result.places[0].sameAs)
        assertNull(result.places[1].sameAs)
        assertEquals(0, result.places[2].sameAs)
    }

    @Test
    fun `이름이 같아도 다른 도시면 합치지 않는다`() {
        // 동명 도시(예: 몽골의 Bulgan 여러 곳)는 따로
        val result = mergeSameCity(listOf(place(1, 10.0), place(3, 12.0)), listOf(7L, 8L), listOf("Bulgan", "Bulgan"), zone)
        assertEquals(2, result.places.size)
        assertNull(result.places[1].sameAs)
    }

    @Test
    fun `이름 없는 장소는 합치지 않는다`() {
        val result = mergeSameCity(listOf(place(1, null), place(2, null)), listOf(null, null), listOf(null, null), zone)
        assertEquals(2, result.places.size)
    }
}
