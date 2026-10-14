package app.wend.sea.data.local

import androidx.room.Entity

/**
 * 사진·영상 한 개에서 읽은 위치. 파일을 열어야만 알 수 있어서 한 번 읽으면 저장해 두고,
 * 파일이 바뀌지 않았으면([modified] 가 같으면) 다시 읽지 않는다. 위치가 없으면 위도·경도가 null.
 */
@Entity(tableName = "media_location", primaryKeys = ["mediaId", "video"])
data class MediaLocationEntity(
    val mediaId: Long,
    val video: Boolean,
    /** MediaStore DATE_MODIFIED(초) */
    val modified: Long,
    val latitude: Double?,
    val longitude: Double?,
)
