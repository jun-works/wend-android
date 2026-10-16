package app.wend.sea.data.analysis

/**
 * 빠진 사진에서 여행에 넣을 사진 한 장(design/wend-prototype/DESIGN_NOTES.md ⑰). 저장하지 않는다 — 처음부터 다시 찾으면 정책대로 새로 묶인다.
 * - 기존 여행에 넣음: [anchor] 는 그 여행의 사진 하나.
 * - 새 여행을 만듦: [anchor] 는 새로 넣는 사진 하나. 같은 [anchor] 끼리 따로 한 여행이 된다.
 * 키는 다른 곳과 같이 (영상인지, id).
 */
data class ManualPin(val key: Pair<Boolean, Long>, val anchor: Pair<Boolean, Long>)

object ManualTrips {

    /**
     * 자동으로 찾은 [trips] 에 [pins] 를 얹는다. [points] 는 지금 보이는 사진·영상(보이지 않는 핀은 건너뜀).
     * 직접 넣은 사진은 자동 여행에서 빼고 핀 자리에 둔다. 넣은 여행을 찾을 수 없으면(그 사진이 여행에서 빠짐) 빠진 사진으로 돌아간다.
     * 촬영 시각이 하나도 없는 여행은 만들지 않는다. 결과는 시작 시각 순서.
     */
    fun apply(trips: List<DetectedTrip>, pins: List<ManualPin>, points: Map<Pair<Boolean, Long>, MediaPoint>): List<DetectedTrip> {
        val pinned = pins.map { it.key }.toSet()
        val auto = trips.map { t -> t.media.filter { (it.video to it.id) !in pinned } }
        val tripOf = HashMap<Pair<Boolean, Long>, Int>()
        auto.forEachIndexed { i, media -> media.forEach { tripOf[it.video to it.id] = i } }

        val added = List(auto.size) { mutableListOf<MediaPoint>() }
        val created = LinkedHashMap<Pair<Boolean, Long>, MutableList<MediaPoint>>()
        for (pin in pins) {
            val point = points[pin.key] ?: continue
            when {
                pin.anchor in pinned -> created.getOrPut(pin.anchor) { mutableListOf() } += point
                else -> tripOf[pin.anchor]?.let { added[it] += point }
            }
        }

        val merged = auto.mapIndexed { i, media -> merge(media, added[i]) } + created.values.map { merge(emptyList(), it) }
        return merged.mapNotNull { media ->
            val times = media.mapNotNull { it.takenAt }
            if (times.isEmpty()) null else DetectedTrip(times.min(), times.max(), media)
        }.sortedBy { it.start }
    }

    /** 찍은 순서를 지키며 [extra] 를 [base] 사이에 끼운다. 시각을 모르는 것은 끝에 */
    private fun merge(base: List<MediaPoint>, extra: List<MediaPoint>): List<MediaPoint> {
        if (extra.isEmpty()) return base
        val timed = extra.filter { it.takenAt != null }.sortedBy { it.takenAt }
        val result = ArrayList<MediaPoint>(base.size + extra.size)
        var next = 0
        for (m in base) {
            val t = m.takenAt
            if (t != null) while (next < timed.size && timed[next].takenAt!! < t) result += timed[next++]
            result += m
        }
        while (next < timed.size) result += timed[next++]
        result += extra.filter { it.takenAt == null }
        return result
    }
}
