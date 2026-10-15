package app.wend.sea.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * 분석으로 찾은 여행. [number] 는 찍은 순서(오래된 것이 1).
 * [cityId] 는 사진이 가장 많은 도시(CityEntity.id). 아직 안 찾았으면 null, 찾았는데 없으면 [NO_CITY].
 * [cityShare] 는 그 도시 사진 비율(0–1), [countryCode] 는 사진이 가장 많은 나라 — 여행 이름을 도시로 할지 나라로 할지 정할 때 쓴다.
 */
@Entity(tableName = "trip")
data class TripEntity(
    @androidx.room.PrimaryKey val number: Int,
    val start: Long,
    val end: Long,
    val mediaCount: Int,
    val cityId: Long? = null,
    val cityShare: Float? = null,
    val countryCode: String? = null,
) {
    companion object {
        const val NO_CITY = 0L
    }
}

@Entity(tableName = "trip_media", primaryKeys = ["tripNumber", "mediaId", "video"])
data class TripMediaEntity(
    val tripNumber: Int,
    val mediaId: Long,
    val video: Boolean,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trip ORDER BY number")
    suspend fun trips(): List<TripEntity>

    @Query("SELECT * FROM trip WHERE number = :number")
    suspend fun trip(number: Int): TripEntity?

    @Query("SELECT * FROM trip_media WHERE tripNumber = :number ORDER BY rowid")
    suspend fun media(number: Int): List<TripMediaEntity>

    /** 여행마다 찍은 순서대로(넣은 순서 = 찍은 순서) */
    @Query("SELECT * FROM trip_media ORDER BY tripNumber, rowid")
    suspend fun allMedia(): List<TripMediaEntity>

    @Query("UPDATE trip SET cityId = :cityId, cityShare = :cityShare, countryCode = :countryCode WHERE number = :number")
    suspend fun setCity(number: Int, cityId: Long, cityShare: Float, countryCode: String?)

    @Query("DELETE FROM trip")
    suspend fun clearTrips()

    @Query("DELETE FROM trip_media")
    suspend fun clearMedia()

    @Upsert
    suspend fun insertTrips(trips: List<TripEntity>)

    @Upsert
    suspend fun insertMedia(media: List<TripMediaEntity>)

    /** 새 분석 결과로 통째로 바꾼다 */
    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>, media: List<TripMediaEntity>) {
        clearTrips()
        clearMedia()
        insertTrips(trips)
        media.chunked(500).forEach { insertMedia(it) }
    }
}
