package app.wend.sea.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MediaLocationDao {
    @Query("SELECT * FROM media_location")
    suspend fun getAll(): List<MediaLocationEntity>

    @Upsert
    suspend fun upsert(items: List<MediaLocationEntity>)

    @Query("DELETE FROM media_location WHERE video = :video AND mediaId IN (:ids)")
    suspend fun delete(video: Boolean, ids: List<Long>)
}
