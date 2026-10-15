package app.wend.sea.data.analysis

/** 장면 묶기에 쓰는 사진 한 장 — 촬영 시각과 저장해 둔 dHash */
data class SceneShot(val id: Long, val takenAt: Long, val hash: Long)

/**
 * 같은 장면을 여러 번 찍은 사진끼리 묶는다 — design/BEST_SHOT.md. 좋은 사진인지는 판정하지 않는다(고르는 건 사용자).
 * 한 장소의 사진을 받아, 촬영 시간순으로 바로 앞 사진과 [sameSceneMs] 안이고 dHash 거리가 [sameSceneHash] 이하면 같은 장면.
 */
class SceneGrouper(
    private val sameSceneMs: Long = 60_000,
    private val sameSceneHash: Int = 12,
) {
    /** 2장 이상인 장면만, 찍은 순서대로 */
    fun group(shots: List<SceneShot>): List<List<SceneShot>> {
        val scenes = mutableListOf<MutableList<SceneShot>>()
        for (shot in shots.sortedBy { it.takenAt }) {
            val prev = scenes.lastOrNull()?.last()
            if (prev != null && shot.takenAt - prev.takenAt <= sameSceneMs &&
                PhotoQuality.hashDistance(prev.hash, shot.hash) <= sameSceneHash
            ) {
                scenes.last() += shot
            } else {
                scenes += mutableListOf(shot)
            }
        }
        return scenes.filter { it.size >= 2 }
    }
}
