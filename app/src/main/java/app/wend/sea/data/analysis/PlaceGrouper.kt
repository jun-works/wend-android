package app.wend.sea.data.analysis

import java.time.Instant
import java.time.ZoneId

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

/** 이름 붙인 장소들 */
data class NamedPlaces(val places: List<Place>, val names: List<String?>)

/**
 * 같은 도시면 같은 장소로 본다: 같은 날 이어진 같은 도시는 한 장소로 합치고,
 * 앞서 들른 도시가 다시 나오면 그 장소 번호를 쓴다([Place.sameAs]). 도시가 없는 장소는 그대로.
 * 이름이 아니라 도시 id 로 비교한다 — 폰 언어가 달라도 묶이는 방식은 같다. [names] 는 그 도시의 사용자 언어 이름.
 */
fun mergeSameCity(places: List<Place>, cityIds: List<Long?>, names: List<String?>, zone: ZoneId): NamedPlaces {
    fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val groups = mutableListOf<MutableList<Int>>()
    for (i in places.indices) {
        val prev = groups.lastOrNull()?.last()
        if (prev != null && cityIds[i] != null && cityIds[i] == cityIds[prev] && day(places[prev].end) == day(places[i].start)) {
            groups.last() += i
        } else {
            groups += mutableListOf(i)
        }
    }
    val newIndex = IntArray(places.size).also { map -> groups.forEachIndexed { k, g -> g.forEach { map[it] = k } } }
    val mergedNames = groups.map { names[it.first()] }
    val mergedIds = groups.map { cityIds[it.first()] }
    val merged = groups.mapIndexed { k, g ->
        val parts = g.map { places[it] }
        val located = parts.filter { it.latitude != null && it.longitude != null }
        val weight = located.map { p -> p.media.count { it.hasLocation }.coerceAtLeast(1).toDouble() }
        val lat = located.takeIf { it.isNotEmpty() }?.let { l -> l.indices.sumOf { l[it].latitude!! * weight[it] } / weight.sum() }
        val lon = located.takeIf { it.isNotEmpty() }?.let { l -> l.indices.sumOf { l[it].longitude!! * weight[it] } / weight.sum() }
        val id = mergedIds[k]
        val sameAs = if (id != null) {
            mergedIds.indexOf(id).takeIf { it < k }
        } else {
            parts.first().sameAs?.let { newIndex[it] }?.takeIf { it < k }
        }
        Place(parts.flatMap { it.media }, lat, lon, sameAs)
    }
    return NamedPlaces(merged, mergedNames)
}
