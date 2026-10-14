package app.wend.sea.data.analysis

import app.wend.sea.data.analysis.TripDetector.Companion.DAY
import app.wend.sea.data.analysis.TripDetector.Companion.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetectorTest {

    private val seoul = 37.5665 to 126.9780
    private val jeju = 33.4996 to 126.5312
    private val busan = 35.1796 to 129.0756
    private val start = 1_700_000_000_000L
    private var nextId = 0L

    private fun shot(t: Long, at: Pair<Double, Double>?) =
        MediaPoint(nextId++, video = false, takenAt = t, latitude = at?.first, longitude = at?.second)

    /** 20주 동안 이틀에 한 번, 집에서 2장씩. 여행 중인 날([away])은 집에서 안 찍는다 */
    private fun everyday(vararg away: IntRange) = (0 until 20 * 7 step 2).filter { d -> away.none { d in it } }.flatMap { d ->
        val t = start + d * DAY + 19 * HOUR
        listOf(shot(t, seoul), shot(t + 40 * 60_000, seoul))
    }

    /** [day] 부터 [days] 일 동안 하루 10~19시에 30분마다 한 장 */
    private fun dense(day: Int, days: Int, at: Pair<Double, Double>?) = (0 until days).flatMap { d ->
        (0 until 18).map { k -> shot(start + (day + d) * DAY + 10 * HOUR + k * 30 * 60_000L, at) }
    }

    @Test
    fun `평소 사진 사이의 촘촘한 여행 한 개를 찾는다`() {
        val trip = dense(day = 31, days = 4, at = jeju)
        val trips = TripDetector().detect(everyday(31..34) + trip)
        assertEquals(1, trips.size)
        assertTrue(trips[0].media.containsAll(trip))
    }

    @Test
    fun `하루 넘게 안 찍어 잘린 여행도 같은 여행지면 다시 잇는다`() {
        val first = dense(day = 31, days = 2, at = jeju)
        val second = dense(day = 34, days = 2, at = jeju)
        val trips = TripDetector().detect(everyday(31..35) + first + second)
        assertEquals(1, trips.size)
        assertTrue(trips[0].media.containsAll(first + second))
    }

    @Test
    fun `다른 여행지는 따로 찾는다`() {
        val trips = TripDetector().detect(everyday(31..33, 61..62) + dense(31, 3, jeju) + dense(61, 2, busan))
        assertEquals(2, trips.size)
    }

    @Test
    fun `위치가 한 장도 없는 급증은 여행이 아니다`() {
        val trips = TripDetector().detect(everyday(31..33) + dense(31, 3, at = null))
        assertEquals(0, trips.size)
    }

    @Test
    fun `위치 없는 사진도 여행 기간 안이면 함께 담긴다`() {
        val trip = dense(31, 3, jeju)
        val noLocation = shot(start + 32 * DAY + 13 * HOUR + 60_000, null)
        val trips = TripDetector().detect(everyday(31..33) + trip + noLocation)
        assertEquals(1, trips.size)
        assertTrue(noLocation in trips[0].media)
    }

    @Test
    fun `집 근처라도 사진이 몰린 이벤트는 그대로 둔다`() {
        val party = dense(31, 1, seoul)
        val trips = TripDetector().detect(everyday(31..31) + party)
        assertEquals(1, trips.size)
        assertTrue(trips[0].media.containsAll(party))
    }

    @Test
    fun `여행 앞뒤의 집 사진은 여행에 넣지 않는다`() {
        val home = everyday(31..34)
        val trip = dense(31, 4, jeju)
        val trips = TripDetector().detect(home + trip)
        assertEquals(1, trips.size)
        assertTrue(trips[0].media.none { it in home })
    }

    @Test
    fun `평소 사진만 있으면 여행이 없다`() {
        assertEquals(0, TripDetector().detect(everyday()).size)
    }
}
