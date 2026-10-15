package app.wend.sea.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import app.wend.sea.data.analysis.PhotoQuality

/**
 * 사진 한 장의 dHash·밝기 값 — design/BEST_SHOT.md. 베스트샷 장면 묶기와 사진 정리(너무 어두운 사진)에 쓴다.
 * 썸네일을 읽어야 해서 한 번 계산하면 저장해 두고, 파일이 바뀌지 않았으면([modified] 가 같으면) 다시 계산하지 않는다.
 * 사진만 저장한다(영상은 대상이 아님).
 */
@Entity(tableName = "media_feature", primaryKeys = ["mediaId"])
data class MediaFeatureEntity(
    val mediaId: Long,
    /** MediaStore DATE_MODIFIED(초) */
    val modified: Long,
    val brightness: Float,
    val darkClip: Float,
    val brightClip: Float,
    val hash: Long,
) {
    companion object {
        fun of(mediaId: Long, modified: Long, q: PhotoQuality) =
            MediaFeatureEntity(mediaId, modified, q.brightness, q.darkClip, q.brightClip, q.hash)
    }
}

/** 값을 계산했을 때의 파일 수정 시각 — 다시 계산할지 볼 때 */
data class FeatureStamp(val mediaId: Long, val modified: Long)

@Dao
interface MediaFeatureDao {
    @Query("SELECT mediaId, modified FROM media_feature")
    suspend fun stamps(): List<FeatureStamp>

    @Query("SELECT * FROM media_feature WHERE mediaId IN (:ids)")
    suspend fun features(ids: List<Long>): List<MediaFeatureEntity>

    @Upsert
    suspend fun upsert(items: List<MediaFeatureEntity>)
}
