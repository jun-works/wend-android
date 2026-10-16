package app.wend.sea.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhotoQualityTest {

    private val w = 96
    private val h = 64

    /** 상자 흐림 — 무작위 점을 덩어리진 장면처럼 만든다 */
    private fun blur(src: IntArray, r: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        var s = 0
        var n = 0
        for (dy in -r..r) for (dx in -r..r) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) {
                s += src[yy * w + xx]
                n++
            }
        }
        s / n
    }

    @Test
    fun `밝기와 날아간 부분`() {
        val q = PhotoQuality.of(IntArray(w * h) { i -> if (i < w * h / 4) 0 else 255 }, w, h)
        assertEquals(191.25f, q.brightness, 0.5f)
        assertEquals(0.25f, q.darkClip, 0.001f)
        assertEquals(0.75f, q.brightClip, 0.001f)
    }

    @Test
    fun `같은 구도는 dHash 가 가깝고 다른 구도는 멀다`() {
        val rnd = Random(7)
        val scene = blur(IntArray(w * h) { rnd.nextInt(256) }, 4)
        val brighter = IntArray(w * h) { (scene[it] + 15).coerceAtMost(255) }
        val other = blur(IntArray(w * h) { rnd.nextInt(256) }, 4)
        val a = PhotoQuality.of(scene, w, h).hash
        assertTrue(PhotoQuality.hashDistance(a, PhotoQuality.of(brighter, w, h).hash) <= 4)
        assertTrue(PhotoQuality.hashDistance(a, PhotoQuality.of(other, w, h).hash) > 16)
    }

    @Test
    fun `너무 어두운 사진은 밝은 것이 하나도 없을 때만`() {
        fun dark(q: PhotoQuality) = PhotoQuality.tooDark(q.brightness, q.darkClip, q.peak)
        // 주머니 속처럼 거의 다 검다
        assertTrue(dark(PhotoQuality.of(IntArray(w * h) { 4 }, w, h)))
        // 검은 하늘의 달: 평균은 아주 낮아도 밝은 덩어리가 있다
        val moon = PhotoQuality.of(IntArray(w * h) { i -> if ((i % w - 48) * (i % w - 48) + (i / w - 32) * (i / w - 32) < 16) 180 else 2 }, w, h)
        assertTrue(moon.brightness < PhotoQuality.TOO_DARK_BRIGHTNESS)
        assertTrue(!dark(moon))
        // 밤 풍경: 대부분 검어도 불빛이 있어 평균이 밝다
        assertTrue(!dark(PhotoQuality.of(IntArray(w * h) { i -> if (i % 5 == 0) 200 else 6 }, w, h)))
        // 어둑한 실내: 평균은 낮아도 검은 픽셀이 많지 않다
        assertTrue(!dark(PhotoQuality.of(IntArray(w * h) { 18 }, w, h)))
    }
}
