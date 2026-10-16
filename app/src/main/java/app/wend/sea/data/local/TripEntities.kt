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

/**
 * 사용자가 붙인 이름. 이름을 여행·장소 안의 사진·영상 하나하나에 붙여 두고, 보여 줄 때 가장 많은 이름을 쓴다.
 * 처음부터 다시 찾으면 지운다. [kind] 는 [KIND_TRIP] 또는 [KIND_PLACE].
 */
@Entity(tableName = "user_name", primaryKeys = ["mediaId", "video", "kind"])
data class UserNameEntity(
    val mediaId: Long,
    val video: Boolean,
    val kind: Int,
    val name: String,
) {
    companion object {
        const val KIND_TRIP = 0
        const val KIND_PLACE = 1
    }
}

/**
 * 사용자가 "여행에서 빼기"로 뺀 사진·영상. 빠진 사진에서 다시 넣으면 이 줄을 지우고, 처음부터 다시 찾으면 모두 지운다.
 */
@Entity(tableName = "excluded_media", primaryKeys = ["mediaId", "video"])
data class ExcludedMediaEntity(val mediaId: Long, val video: Boolean)

/**
 * 사용자가 고른 베스트샷(사진만) — design/BEST_SHOT.md. 같은 장면 묶음마다 한 장. 처음부터 다시 찾으면 지운다.
 * 장면은 화면을 열 때마다 계산하므로, 사진 id 만 저장하고 장면에는 묶지 않는다.
 */
@Entity(tableName = "best_shot", primaryKeys = ["mediaId"])
data class BestShotEntity(val mediaId: Long)

/** 여행마다 사진에 붙은 이름과 그 장수 */
data class TripNameCount(val tripNumber: Int, val name: String, val count: Int)

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

    @Query(
        "SELECT tm.tripNumber AS tripNumber, un.name AS name, COUNT(*) AS count FROM trip_media tm " +
            "JOIN user_name un ON un.mediaId = tm.mediaId AND un.video = tm.video AND un.kind = 0 " +
            "GROUP BY tm.tripNumber, un.name",
    )
    suspend fun tripNameCounts(): List<TripNameCount>

    @Query("SELECT * FROM user_name WHERE kind = 1")
    suspend fun placeNames(): List<UserNameEntity>

    @Query("DELETE FROM user_name WHERE kind = :kind AND mediaId = :mediaId AND video = :video")
    suspend fun deleteName(kind: Int, mediaId: Long, video: Boolean)

    @Upsert
    suspend fun upsertNames(names: List<UserNameEntity>)

    /** 사진들에 이름을 붙인다. [name] 이 null 이면 붙여 둔 이름을 지워 자동 이름으로 돌아간다 */
    @Transaction
    suspend fun setName(kind: Int, media: List<Pair<Boolean, Long>>, name: String?) {
        if (name == null) {
            media.forEach { (video, id) -> deleteName(kind, id, video) }
        } else {
            media.chunked(500).forEach { chunk -> upsertNames(chunk.map { (video, id) -> UserNameEntity(id, video, kind, name) }) }
        }
    }

    @Upsert
    suspend fun exclude(media: ExcludedMediaEntity)

    @Query("DELETE FROM excluded_media WHERE mediaId = :mediaId AND video = :video")
    suspend fun unexclude(mediaId: Long, video: Boolean)

    @Query("DELETE FROM excluded_media")
    suspend fun clearExcluded()

    @Query("DELETE FROM best_shot")
    suspend fun clearBestShots()

    @Query("DELETE FROM user_name")
    suspend fun clearNames()

    /** 처음부터 다시 찾기: 사용자가 고친 것(뺀 사진 · 베스트샷 · 이름)을 모두 지운다. 넣은 사진·만든 여행은 여행과 함께 새로 찾는다 */
    @Transaction
    suspend fun resetUserChoices() {
        clearExcluded()
        clearBestShots()
        clearNames()
    }

    @Query("DELETE FROM trip_media WHERE tripNumber = :tripNumber AND mediaId = :mediaId AND video = :video")
    suspend fun deleteTripMedia(tripNumber: Int, mediaId: Long, video: Boolean): Int

    @Query("UPDATE trip SET mediaCount = mediaCount - 1 WHERE number = :number")
    suspend fun decrementCount(number: Int)

    /** 사진 한 장을 여행에서 덜어 낸다(여행에서 빼기). 휴지통은 여기를 쓰지 않는다 — 되살리면 돌아와야 해서 */
    @Transaction
    suspend fun removeFromTrip(tripNumber: Int, mediaId: Long, video: Boolean, exclude: Boolean) {
        if (exclude) exclude(ExcludedMediaEntity(mediaId, video))
        if (deleteTripMedia(tripNumber, mediaId, video) > 0) decrementCount(tripNumber)
    }

    @Query("SELECT mediaId FROM best_shot")
    suspend fun bestShots(): List<Long>

    @Query("DELETE FROM best_shot WHERE mediaId IN (:ids)")
    suspend fun deleteBestShots(ids: List<Long>)

    @Upsert
    suspend fun insertBestShots(items: List<BestShotEntity>)

    /** [scope] 사진들의 베스트샷을 지우고 [picks] 로 바꾼다 */
    @Transaction
    suspend fun replaceBestShots(scope: List<Long>, picks: List<Long>) {
        scope.chunked(500).forEach { deleteBestShots(it) }
        insertBestShots(picks.map { BestShotEntity(it) })
    }

    /** 새 분석 결과로 통째로 바꾼다 */
    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>, media: List<TripMediaEntity>) {
        clearTrips()
        clearMedia()
        insertTrips(trips)
        media.chunked(500).forEach { insertMedia(it) }
    }
}
