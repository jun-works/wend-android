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
}
