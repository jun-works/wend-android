package app.wend.sea.data.analysis

import app.wend.sea.data.analysis.TripDetector.Companion.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceGrouperTest {

    private val station = 35.1152 to 129.0422
    private val gamcheon = 35.0975 to 129.0106 // 약 3.5km
    private val stationNear = 35.1160 to 129.0430 // 약 120m
    private var nextId = 0L

    private fun shot(minute: Int, at: Pair<Double, Double>?) =
        MediaPoint(nextId++, false, minute * 60_000L, at?.first, at?.second)

    @Test
    fun `1km 넘게 옮기면 새 장소`() {
        val places = PlaceGrouper().group(listOf(shot(0, station), shot(10, station), shot(60, gamcheon), shot(70, gamcheon)))
        assertEquals(2, places.size)
        assertEquals(2, places[1].media.size)
    }

    @Test
    fun `2시간 넘게 비면 같은 곳이라도 새 장소`() {
        val places = PlaceGrouper().group(listOf(shot(0, station), shot((3 * HOUR / 60_000).toInt(), station)))
        assertEquals(2, places.size)
    }

    @Test
    fun `위치 없는 사진은 시간상 이어진 장소에 붙는다`() {
        val places = PlaceGrouper().group(listOf(shot(0, station), shot(5, null), shot(10, station)))
        assertEquals(1, places.size)
        assertEquals(3, places[0].media.size)
    }

    @Test
    fun `앞서 들른 곳과 300m 안이면 같은 장소로 표시`() {
        val places = PlaceGrouper().group(
            listOf(shot(0, station), shot(60, gamcheon), shot(120, stationNear)),
        )
        assertEquals(3, places.size)
        assertNull(places[0].sameAs)
        assertNull(places[1].sameAs)
        assertEquals(0, places[2].sameAs)
    }
}
