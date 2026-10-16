package app.wend.sea.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualTripsTest {

    private fun p(id: Long, t: Long?) = MediaPoint(id, video = false, takenAt = t, latitude = null, longitude = null)
    private fun k(id: Long) = false to id
    private fun trip(vararg m: MediaPoint) = DetectedTrip(m.mapNotNull { it.takenAt }.min(), m.mapNotNull { it.takenAt }.max(), m.toList())
    private fun ids(t: DetectedTrip) = t.media.map { it.id }

    private val a = trip(p(1, 100), p(2, 200), p(3, 300))
    private val b = trip(p(10, 1000), p(11, 1100))
    private val points = (listOf(a, b).flatMap { it.media } + listOf(p(20, 250), p(21, 5000), p(22, 5100), p(23, null)))
        .associateBy { it.video to it.id }

    @Test
    fun `기존 여행에 넣은 사진은 찍은 순서 자리에 들어가고 기간이 늘어난다`() {
        val out = ManualTrips.apply(listOf(a, b), listOf(ManualPin(k(20), k(2)), ManualPin(k(21), k(2))), points)
        assertEquals(listOf(1L, 2L, 20L, 3L, 21L), ids(out[0]))
        assertEquals(5000L, out[0].end)
    }

    @Test
    fun `새 여행은 따로 한 여행이 되고 시작 순서로 놓인다`() {
        val out = ManualTrips.apply(listOf(a, b), listOf(ManualPin(k(21), k(21)), ManualPin(k(22), k(21)), ManualPin(k(23), k(21))), points)
        assertEquals(3, out.size)
        assertEquals(listOf(21L, 22L, 23L), ids(out[2]))
    }

    @Test
    fun `다른 여행에 든 사진을 넣으면 그 여행에서 옮긴다`() {
        val detected = listOf(trip(p(1, 100), p(2, 200), p(3, 300)), trip(p(10, 1000), p(11, 1100), p(20, 250)))
        val out = ManualTrips.apply(detected, listOf(ManualPin(k(20), k(1))), points)
        assertEquals(listOf(1L, 2L, 20L, 3L), ids(out[0]))
        assertEquals(listOf(10L, 11L), ids(out[1]))
    }

    @Test
    fun `넣을 여행이 없거나 보이지 않는 사진은 건너뛴다`() {
        val out = ManualTrips.apply(listOf(b), listOf(ManualPin(k(20), k(2)), ManualPin(k(99), k(10))), points)
        assertEquals(listOf(listOf(10L, 11L)), out.map(::ids))
    }

    @Test
    fun `시각이 하나도 없는 새 여행은 만들지 않는다`() {
        val out = ManualTrips.apply(emptyList(), listOf(ManualPin(k(23), k(23))), points)
        assertEquals(0, out.size)
    }
}
