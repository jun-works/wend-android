package app.wend.sea.data.analysis

/**
 * 흑백 이미지 한 장에서 읽는 값 — design/BEST_SHOT.md. [luma] 는 0–255 밝기, 가로 [width] 세로 [height].
 * 좋은 사진인지 판정하지 않는다: 같은 장면 묶기(dHash)와 너무 어두운 사진 찾기(밝기)에만 쓴다.
 * 안드로이드에 기대지 않아 단위 테스트로 확인할 수 있다.
 */
data class PhotoQuality(
    /** 평균 밝기 0–255 */
    val brightness: Float,
    /** 거의 검은 픽셀 비율 0–1 */
    val darkClip: Float,
    /** 거의 흰 픽셀 비율 0–1 */
    val brightClip: Float,
    /** 같은 장면 찾기용 dHash 64bit */
    val hash: Long,
    /** 가장 밝은 부분의 밝기 0–255(밝은 쪽 0.1% 를 뺀 값) — 어두운 화면 속 달·불빛처럼 찍으려던 것이 있는지 */
    val peak: Int,
) {
    companion object {
        const val DARK = 12
        const val BRIGHT = 243

        /** 너무 어두운 사진(사진 정리하기 후보): 평균 밝기가 이보다 낮고 */
        const val TOO_DARK_BRIGHTNESS = 20f
        /** 거의 검은 픽셀이 이보다 많고 */
        const val TOO_DARK_CLIP = 0.7f
        /**
         * 가장 밝은 부분도 이보다 어두우면 — 화면 어디에도 밝은 것이 없는 사진(주머니 속·렌즈 가림·안 찍힌 밤하늘)만.
         * 평균만 보면 밤하늘의 달도 걸린다(2026-10-16 실측: 달 사진 peak 127·205, 아무것도 안 찍힌 사진 14·23)
         */
        const val TOO_DARK_PEAK = 48

        fun tooDark(brightness: Float, darkClip: Float, peak: Int): Boolean =
            brightness < TOO_DARK_BRIGHTNESS && darkClip > TOO_DARK_CLIP && peak < TOO_DARK_PEAK

        fun of(luma: IntArray, width: Int, height: Int): PhotoQuality {
            require(luma.size == width * height && width >= 9 && height >= 8)
            var sum = 0L
            var dark = 0
            var bright = 0
            val histogram = IntArray(256)
            for (v in luma) {
                sum += v
                if (v <= DARK) dark++
                if (v >= BRIGHT) bright++
                histogram[v.coerceIn(0, 255)]++
            }
            val n = luma.size.toFloat()
            return PhotoQuality(
                brightness = sum / n,
                darkClip = dark / n,
                brightClip = bright / n,
                hash = dHash(luma, width, height),
                peak = peak(histogram, luma.size),
            )
        }

        /** 밝은 쪽에서 0.1% 를 지나는 밝기 — 튀는 점 몇 개에 끌려가지 않게 */
        private fun peak(histogram: IntArray, count: Int): Int {
            val skip = count / 1000
            var seen = 0
            for (v in 255 downTo 0) {
                seen += histogram[v]
                if (seen > skip) return v
            }
            return 0
        }

        /** 9×8 로 줄인 뒤 옆 칸보다 밝으면 1 — 구도가 같으면 밝기·크기가 조금 달라도 비슷한 값 */
        fun dHash(luma: IntArray, width: Int, height: Int): Long {
            val small = IntArray(9 * 8)
            for (sy in 0 until 8) {
                val ya = sy * height / 8
                val yb = maxOf((sy + 1) * height / 8, ya + 1)
                for (sx in 0 until 9) {
                    val xa = sx * width / 9
                    val xb = maxOf((sx + 1) * width / 9, xa + 1)
                    var s = 0L
                    for (y in ya until yb) for (x in xa until xb) s += luma[y * width + x]
                    small[sy * 9 + sx] = (s / ((yb - ya) * (xb - xa))).toInt()
                }
            }
            var hash = 0L
            var bit = 0
            for (sy in 0 until 8) {
                for (sx in 0 until 8) {
                    if (small[sy * 9 + sx] > small[sy * 9 + sx + 1]) hash = hash or (1L shl bit)
                    bit++
                }
            }
            return hash
        }

        fun hashDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
    }
}
