package app.wend.sea.data.analysis

/** 여행 안의 장소 하나: 시간 순서대로 이어진 사진 묶음 */
data class Place(
    val media: List<MediaPoint>,
    /** 위치 있는 사진들의 중심. 위치가 하나도 없으면 null */
    val latitude: Double?,
    val longitude: Double?,
    /** 앞서 들른 장소와 같은 곳이면 그 장소 순번(지도 번호를 하나만 보이게) */
    val sameAs: Int?,
) {
    val start: Long get() = media.first().takenAt!!
    val end: Long get() = media.last().takenAt!!
}

/**
 * 여행 사진을 장소 단위로 나눈다. 앞 장소 중심에서 [moveKm] 넘게 옮기거나 [gapMs] 넘게 비면 새 장소.
 * 위치 없는 사진은 시간상 이어지는 장소에 붙는다.
 */
class PlaceGrouper(
    private val moveKm: Double = 1.0,
    private val gapMs: Long = 2 * TripDetector.HOUR,
    private val sameSpotKm: Double = 0.3,
) {
    fun group(media: List<MediaPoint>): List<Place> {
        val timed = media.filter { it.takenAt != null }.sortedBy { it.takenAt }
        if (timed.isEmpty()) return emptyList()

        val groups = mutableListOf(mutableListOf(timed.first()))
        var center = timed.first().takeIf { it.hasLocation }?.let { it.latitude!! to it.longitude!! }
        var located = if (center != null) 1 else 0
        for (m in timed.drop(1)) {
            val current = groups.last()
            val gap = m.takenAt!! - current.last().takenAt!!
            val moved = m.hasLocation && center != null &&
                TripDetector.haversineKm(center.first, center.second, m.latitude!!, m.longitude!!) > moveKm
            if (gap > gapMs || moved) {
                groups += mutableListOf(m)
                center = if (m.hasLocation) m.latitude!! to m.longitude!! else null
                located = if (m.hasLocation) 1 else 0
            } else {
                current += m
                if (m.hasLocation) {
                    // 중심을 평균으로 갱신
                    located++
                    center = if (center == null) {
                        m.latitude!! to m.longitude!!
                    } else {
                        center.first + (m.latitude!! - center.first) / located to center.second + (m.longitude!! - center.second) / located
                    }
                }
            }
        }

        val places = mutableListOf<Place>()
        for (g in groups) {
            val loc = g.filter { it.hasLocation }
            val lat = loc.takeIf { it.isNotEmpty() }?.map { it.latitude!! }?.average()
            val lon = loc.takeIf { it.isNotEmpty() }?.map { it.longitude!! }?.average()
            val sameAs = if (lat == null || lon == null) {
                null
            } else {
                places.indexOfFirst { p ->
                    p.sameAs == null && p.latitude != null &&
                        TripDetector.haversineKm(p.latitude, p.longitude!!, lat, lon) <= sameSpotKm
                }.takeIf { it >= 0 }
            }
            places += Place(g, lat, lon, sameAs)
        }
        return places
    }
}
