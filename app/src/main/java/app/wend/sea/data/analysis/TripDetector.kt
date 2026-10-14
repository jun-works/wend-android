package app.wend.sea.data.analysis

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** 분석에 쓰는 사진·영상 한 개. 시간은 촬영 시각(ms), 위치는 EXIF/영상 메타데이터. */
data class MediaPoint(
    val id: Long,
    val video: Boolean,
    val takenAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

data class DetectedTrip(
    val start: Long,
    val end: Long,
    val media: List<MediaPoint>,
)

/**
 * 여행 찾기 — design/TRIP_DETECTION.md 의 정책을 그대로 따른다. 수치는 실제 데이터로 다듬을 기본값.
 *
 * 1. 시간 간격으로 이벤트 나누기(주변 간격 대비 유난히 긴 곳에서 자름)
 * 2. 평소보다 촘촘하고 많은 이벤트만 여행 후보
 * 3. 위치가 한 장도 없는 후보는 제외
 * 4. 생활권 밖 같은 여행지면 앞뒤 이벤트를 붙이고, 잘린 여행을 다시 이음
 * 5. 위치 없는 사진은 촬영 시간으로 이벤트에 들어가 있으므로 그대로 따라감
 */
class TripDetector(private val params: Params = Params()) {

    data class Params(
        /** 주변 간격 중앙값의 몇 배부터 자를지 */
        val gapRatio: Double = 6.0,
        /** 이보다 짧은 간격에서는 자르지 않음 */
        val minCutGapMs: Long = 2 * HOUR,
        /** 주변 간격을 볼 때 앞뒤로 몇 개 */
        val gapWindow: Int = 10,
        /** 평소 이벤트 대비 촘촘함 배수 */
        val densityRatio: Double = 2.0,
        /** 촘촘함은 장수 ÷ 기간. 기간이 이보다 짧으면 이 값으로 본다(짧은 이벤트가 과하게 촘촘해 보이지 않게) */
        val densitySpanFloorMs: Long = DAY,
        val minTripShots: Int = 15,
        /** 이 안에 찍힌 사진은 연사로 보고 1장으로 셈 */
        val burstMs: Long = 2_000,
        /** 생활권 칸 크기(도). 약 300m */
        val homeCellDeg: Double = 0.003,
        /** 서로 다른 몇 주 이상 나타나면 생활권 */
        val homeMinWeeks: Int = 6,
        /** 생활권에서 이만큼 떨어져야 여행지 */
        val awayFromHomeKm: Double = 20.0,
        /** 여행 사진들과 이 거리 안이면 같은 여행지 */
        val sameDestinationKm: Double = 50.0,
        /** 잘린 두 여행 사이가 이 안이면 다시 이음 */
        val rejoinGapMs: Long = 3 * DAY,
    )

    /** 짧은 공백으로 나눈 사진 묶음(세션)들 — 밤잠 정도의 공백은 이벤트 안에 남는다 */
    private class Event(val sessions: List<List<MediaPoint>>) {
        val media = sessions.flatten()
        val start get() = media.first().takenAt!!
        val end get() = media.last().takenAt!!
    }

    fun detect(all: List<MediaPoint>): List<DetectedTrip> {
        val timed = all.filter { it.takenAt != null }.sortedBy { it.takenAt }
        if (timed.size < 2) return emptyList()

        val home = homeCells(all)
        fun outside(p: MediaPoint) = p.hasLocation && home.none { distanceKm(it, p) < params.awayFromHomeKm }
        fun homeOnly(session: List<MediaPoint>) = session.any { it.hasLocation } && session.none(::outside)

        // 여행 앞뒤에 붙은 집 사진(출발 전날 저녁 등)은 떼어낸다. 전부 집이면 동네 이벤트로 그대로 둔다.
        val events = splitByGaps(timed).map { e ->
            if (e.sessions.all(::homeOnly)) {
                e
            } else {
                Event(e.sessions.dropWhile(::homeOnly).dropLastWhile(::homeOnly))
            }
        }
        val shots = events.map(::shotCount)
        val density = events.mapIndexed { i, e -> shots[i] / max(params.densitySpanFloorMs, e.end - e.start).toDouble() }
        val usualDensity = median(density.filterIndexed { i, _ -> shots[i] >= 2 }) ?: return emptyList()

        // 2·3단계: 급증한 이벤트 중 위치가 있는 것만 후보
        val isCandidate = BooleanArray(events.size) { i ->
            shots[i] >= params.minTripShots &&
                density[i] >= usualDensity * params.densityRatio &&
                events[i].media.any { it.hasLocation }
        }

        // 4단계: 후보마다 앞뒤 이벤트 중 같은 여행지(생활권 밖)를 붙이고, 가까운 후보끼리 잇는다
        val owner = IntArray(events.size) { -1 }
        var tripId = 0
        var i = 0
        while (i < events.size) {
            if (!isCandidate[i] || owner[i] >= 0) {
                i++
                continue
            }
            val places = events[i].media.filter(::outside).toMutableList()
            owner[i] = tripId
            var last = i
            fun sameDestination(e: Event) = places.isNotEmpty() && e.media.any { m ->
                outside(m) && places.any { distanceKm(it, m) < params.sameDestinationKm }
            }
            // 뒤로 붙이기: 같은 여행지 이벤트, 또는 같은 여행지이면서 가까운 다음 후보
            var j = i + 1
            while (j < events.size) {
                val e = events[j]
                val near = e.start - events[last].end <= params.rejoinGapMs
                if (!near || !sameDestination(e)) break
                owner[j] = tripId
                places += e.media.filter(::outside)
                last = j
                j++
            }
            // 앞으로 붙이기(후보가 아닌 이벤트만)
            var k = i - 1
            while (k >= 0 && owner[k] < 0 && !isCandidate[k]) {
                val e = events[k]
                if (events[k + 1].start - e.end > params.rejoinGapMs || !sameDestination(e)) break
                owner[k] = tripId
                k--
            }
            tripId++
            i = last + 1
        }

        return (0 until tripId).map { id ->
            val media = events.indices.filter { owner[it] == id }.flatMap { events[it].media }
            DetectedTrip(media.first().takenAt!!, media.last().takenAt!!, media)
        }
    }

    private fun splitByGaps(timed: List<MediaPoint>): List<Event> {
        val gaps = LongArray(timed.size - 1) { timed[it + 1].takenAt!! - timed[it].takenAt!! }
        // 평소 사진 사이 간격(며칠)보다 짧은 밤잠 정도의 공백에서는 자르지 않는다 — 여러 날짜 여행이 한 덩어리로 남게
        val usualLongGap = median(gaps.filter { it >= params.minCutGapMs }.map { it.toDouble() })
        val cutFloor = minOf(DAY.toDouble(), (usualLongGap ?: DAY.toDouble()) / 2)
        val events = mutableListOf(mutableListOf(mutableListOf(timed.first())))
        for (g in gaps.indices) {
            val from = max(0, g - params.gapWindow)
            val to = minOf(gaps.lastIndex, g + params.gapWindow)
            val around = (from..to).filter { it != g }.map { gaps[it].toDouble() }
            val local = median(around) ?: 0.0
            val newSession = gaps[g] >= params.minCutGapMs && gaps[g] >= local * params.gapRatio
            val newEvent = newSession && gaps[g] >= cutFloor
            when {
                newEvent -> events += mutableListOf(mutableListOf(timed[g + 1]))
                newSession -> events.last() += mutableListOf(timed[g + 1])
                else -> events.last().last() += timed[g + 1]
            }
        }
        return events.map(::Event)
    }

    /** 연사(2초 안)는 1장으로 셈 */
    private fun shotCount(e: Event): Int {
        var count = 0
        var prev = Long.MIN_VALUE
        for (m in e.media) {
            val t = m.takenAt!!
            if (prev == Long.MIN_VALUE || t - prev > params.burstMs) count++
            prev = t
        }
        return count
    }

    /** 서로 다른 여러 주에 고르게 나타나는 약 300m 칸 = 생활권. 칸 중심 좌표들 */
    private fun homeCells(all: List<MediaPoint>): List<Pair<Double, Double>> {
        val weeks = HashMap<Pair<Long, Long>, MutableSet<Long>>()
        for (m in all) {
            if (!m.hasLocation || m.takenAt == null) continue
            val cell = (m.latitude!! / params.homeCellDeg).roundToLong() to (m.longitude!! / params.homeCellDeg).roundToLong()
            weeks.getOrPut(cell) { HashSet() } += m.takenAt / WEEK
        }
        return weeks.filterValues { it.size >= params.homeMinWeeks }.keys
            .map { it.first * params.homeCellDeg to it.second * params.homeCellDeg }
    }

    private fun distanceKm(a: MediaPoint, b: MediaPoint) = distanceKm(a.latitude!! to a.longitude!!, b)

    private fun distanceKm(a: Pair<Double, Double>, b: MediaPoint) = haversineKm(a.first, a.second, b.latitude!!, b.longitude!!)

    companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val WEEK = 7 * DAY

        fun median(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }

        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            fun rad(d: Double) = d * PI / 180
            val dLat = rad(lat2 - lat1)
            val dLon = rad(lon2 - lon1)
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * 6371.0 * asin(sqrt(h))
        }
    }
}
