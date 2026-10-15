package app.wend.sea.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGrouperTest {

    private var nextId = 0L

    private fun shot(second: Int, hash: Long = 0L) = SceneShot(nextId++, second * 1_000L, hash)

    @Test
    fun `가깝게 찍은 비슷한 사진은 한 장면`() {
        val scenes = SceneGrouper().group(listOf(shot(0), shot(5, hash = 1L), shot(9, hash = 3L)))
        assertEquals(listOf(3), scenes.map { it.size })
    }

    @Test
    fun `한 번만 찍은 사진은 장면으로 보여 주지 않는다`() {
        val scenes = SceneGrouper().group(listOf(shot(0, hash = 0L), shot(5, hash = -1L), shot(10, hash = 0x00FF00FF00FF00FFL)))
        assertTrue(scenes.isEmpty())
    }

    @Test
    fun `비슷해도 60초 넘게 떨어지면 다른 장면`() {
        val scenes = SceneGrouper().group(listOf(shot(0), shot(10), shot(100), shot(110)))
        assertEquals(listOf(2, 2), scenes.map { it.size })
    }

    @Test
    fun `모양이 다르면 바로 이어 찍어도 다른 장면`() {
        val scenes = SceneGrouper().group(listOf(shot(0, hash = 0L), shot(2, hash = 0L), shot(4, hash = -1L), shot(6, hash = -1L)))
        assertEquals(2, scenes.size)
    }

    @Test
    fun `찍은 순서대로 묶는다`() {
        val a = shot(30)
        val b = shot(0)
        val c = shot(15)
        val scene = SceneGrouper().group(listOf(a, b, c)).single()
        assertEquals(listOf(b.id, c.id, a.id), scene.map { it.id })
    }
}
