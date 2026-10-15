package app.wend.sea.data.analysis

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.wend.sea.data.UserRepository
import app.wend.sea.data.geo.CityEntity
import app.wend.sea.data.geo.CityFinder
import app.wend.sea.data.local.MediaFeatureDao
import app.wend.sea.data.local.TripDao
import app.wend.sea.data.local.TripEntity
import app.wend.sea.data.local.UserNameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 찾은 여행 하나. [number] 는 찍은 순서(오래된 것이 1), [city] 는 자동으로 붙인 이름(도시나 나라, 근처에 도시가 없으면 null),
 * [userName] 은 사용자가 바꾼 이름.
 */
data class Trip(
    val number: Int,
    val start: Long,
    val end: Long,
    val mediaCount: Int,
    val city: String? = null,
    val userName: String? = null,
)

/** 여행 상세: 장소들과, 장소마다 자동 이름(가장 가까운 도시의 사용자 언어 이름, 없으면 null)과 사용자가 바꾼 이름 */
data class TripDetail(val trip: Trip, val places: List<Place>, val placeCities: List<String?>, val placeUserNames: List<String?>)

sealed interface AnalysisState {
    /** 분석을 시작한 적 없음 */
    data object Idle : AnalysisState

    data class Running(val phase: AnalysisPhase, val done: Int, val total: Int) : AnalysisState

    data object Finished : AnalysisState

    data object Failed : AnalysisState
}

@Singleton
class AnalysisRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripDao: TripDao,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val cityFinder: CityFinder,
    private val cityPicker: TripCityPicker,
    private val featureDao: MediaFeatureDao,
) {
    private val workManager = WorkManager.getInstance(context)

    /** 이미 돌고 있으면 그대로 둔다 */
    fun start() {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnalysisWorker>().build(),
        )
    }

    val state: Flow<AnalysisState> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        val info = infos.lastOrNull() ?: return@map AnalysisState.Idle
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> AnalysisState.Running(AnalysisPhase.Reading, 0, 0)
            WorkInfo.State.RUNNING -> AnalysisState.Running(
                phase = AnalysisPhase.entries[info.progress.getInt(AnalysisWorker.KEY_PHASE, 0)],
                done = info.progress.getInt(AnalysisWorker.KEY_DONE, 0),
                total = info.progress.getInt(AnalysisWorker.KEY_TOTAL, 0),
            )
            WorkInfo.State.SUCCEEDED -> AnalysisState.Finished
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> AnalysisState.Failed
        }
    }

    val summary: Flow<AnalysisSummary?> = userRepository.analysisSummary

    suspend fun trips(): List<Trip> {
        FeatureWorker.ensure(context)
        val entities = tripDao.trips().map { if (it.cityId == null) withCity(it) else it }
        val cities = cityFinder.byIds(entities.mapNotNull { it.cityId })
        val locale = locale()
        val userNames = tripDao.tripNameCounts().groupBy { it.tripNumber }.mapValues { (_, c) -> c.maxBy { it.count }.name }
        return entities.map { Trip(it.number, it.start, it.end, it.mediaCount, title(it, cities, locale), userNames[it.number]) }
    }

    /**
     * 여행 상세는 장소 묶기·지명 찾기로 0.5초쯤 걸려서, 목록이 뜰 때 미리 읽어 두고(prefetch) 열 때 바로 쓴다.
     * 여행 행(장수·기간·도시)이나 사용자 이름, 언어가 바뀌면 다시 계산한다.
     */
    private val detailLock = Mutex()
    private val detailCache = java.util.concurrent.ConcurrentHashMap<Int, CachedDetail>()
    private var namesVersion = 0

    private data class CachedDetail(val entity: TripEntity, val namesVersion: Int, val locale: Locale, val detail: TripDetail)

    /** 목록에 보이는 여행들의 상세를 미리 읽는다 */
    suspend fun prefetchTripDetails(numbers: List<Int>) = numbers.forEach { tripDetail(it) }

    /** 이미 읽어 둔 상세(기다리지 않는다). 화면 첫 그림용 — 바뀌었을 수 있어 [tripDetail] 로 다시 확인한다 */
    fun cachedTripDetail(number: Int): TripDetail? = detailCache[number]?.detail

    /** 여행 하나와, 그 사진들을 장소 단위로 나눈 것 */
    suspend fun tripDetail(number: Int): TripDetail? = detailLock.withLock {
        val entity = tripDao.trip(number) ?: return@withLock null
        val locale = locale()
        detailCache[number]?.takeIf { it.entity == entity && it.namesVersion == namesVersion && it.locale == locale }?.let { return@withLock it.detail }
        loadTripDetail(entity).also { if (it != null) detailCache[number] = CachedDetail(entity, namesVersion, locale, it) }
    }

    private suspend fun loadTripDetail(entity: TripEntity): TripDetail? {
        val number = entity.number
        val keys = tripDao.media(number).map { it.video to it.mediaId }
        val points = mediaRepository.points(keys)
        val places = withContext(Dispatchers.Default) { PlaceGrouper().group(points) }
        val locale = locale()
        val named = if (entity.cityId == null) withCity(entity) else entity
        val name = title(named, cityFinder.byIds(listOfNotNull(named.cityId)), locale)
        // 도시는 위치만으로 고르고(언어와 무관), 이름만 사용자 언어로 — 없으면 영어
        val placeCities = places.map { p ->
            if (p.latitude == null || p.longitude == null) null else cityFinder.nearestCity(p.latitude, p.longitude, locale)
        }
        val merged = withContext(Dispatchers.Default) {
            mergeSameCity(places, placeCities.map { it?.id }, placeCities.map { it?.name }, ZoneId.systemDefault())
        }
        // 사용자 이름은 자동 이름으로 합친 뒤에 얹는다 — 이름을 바꿔도 장소를 합치거나 나누지 않는다
        val userPlaceNames = tripDao.placeNames().associate { (it.video to it.mediaId) to it.name }
        val placeUserNames = merged.places.map { p ->
            p.media.mapNotNull { userPlaceNames[it.video to it.id] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }
        val userName = tripDao.tripNameCounts().filter { it.tripNumber == number }.maxByOrNull { it.count }?.name
        return TripDetail(Trip(entity.number, entity.start, entity.end, points.size, name, userName), merged.places, merged.names, placeUserNames)
    }

    /**
     * 장소마다 같은 장면을 여러 번 찍은 사진 묶음(design/BEST_SHOT.md) — 2장 이상인 장면만, 순서는 [TripDetail.places] 와 같다.
     * 묶음이 없는 장소는 빈 목록. 값을 아직 계산하지 않은 사진은 빠진다.
     */
    suspend fun similarScenes(detail: TripDetail): List<List<List<MediaThumbnail>>> {
        val hashes = featureDao.features(detail.places.flatMap { p -> p.media.filter { !it.video }.map { it.id } })
            .associate { it.mediaId to it.hash }
        val grouper = SceneGrouper()
        return withContext(Dispatchers.Default) {
            detail.places.map { place ->
                val shots = place.media.mapNotNull { m ->
                    val hash = hashes[m.id]?.takeIf { !m.video } ?: return@mapNotNull null
                    SceneShot(m.id, m.takenAt ?: return@mapNotNull null, hash)
                }
                grouper.group(shots).map { scene -> scene.map { MediaThumbnail(it.id, video = false) } }
            }
        }
    }

    /** 사진 한 장을 여행에서 뺀다. 다시 분석해도 넣지 않고, 빠진 사진에 남는다 */
    suspend fun excludeFromTrip(number: Int, media: MediaThumbnail) =
        tripDao.removeFromTrip(number, media.mediaId, media.video, exclude = true)

    /** 휴지통으로 옮긴 사진을 여행에서 덜어 낸다 */
    suspend fun removeTrashed(number: Int, media: MediaThumbnail) =
        tripDao.removeFromTrip(number, media.mediaId, media.video, exclude = false)

    /** 여행 이름을 바꾼다. null 이면 자동 이름으로 돌아간다 */
    suspend fun renameTrip(number: Int, name: String?) {
        tripDao.setName(UserNameEntity.KIND_TRIP, tripDao.media(number).map { it.video to it.mediaId }, name)
        detailLock.withLock { namesVersion++ }
    }

    /** 장소(그 사진들) 이름을 바꾼다. null 이면 자동 이름으로 돌아간다 */
    suspend fun renamePlace(media: List<Pair<Boolean, Long>>, name: String?) {
        tripDao.setName(UserNameEntity.KIND_PLACE, media, name)
        detailLock.withLock { namesVersion++ }
    }

    /** 도시를 붙이기 전에 찾은 여행(앱 업데이트 전 분석 결과)에 도시를 찾아 저장한다 */
    private suspend fun withCity(entity: TripEntity): TripEntity {
        val points = mediaRepository.points(tripDao.media(entity.number).map { it.video to it.mediaId })
        val city = cityPicker.pick(points)
        tripDao.setCity(entity.number, city.cityId, city.share, city.countryCode)
        return entity.copy(cityId = city.cityId, cityShare = city.share, countryCode = city.countryCode)
    }

    private fun title(entity: TripEntity, cities: Map<Long, CityEntity>, locale: Locale): String? =
        tripTitle(entity.cityId?.let(cities::get), entity.cityShare ?: 0f, entity.countryCode, locale)

    private fun locale(): Locale = context.resources.configuration.locales[0]

    /** 여행마다 카드에 보일 사진 3장: 첫 장 · 가운데 장 · 마지막 장 */
    suspend fun covers(): Map<Int, List<MediaThumbnail>> =
        tripDao.allMedia().groupBy { it.tripNumber }.mapValues { (_, media) ->
            listOf(0, media.size / 2, media.lastIndex).distinct().filter { it in media.indices }
                .map { MediaThumbnail(media[it].mediaId, media[it].video) }
        }

    private companion object {
        const val WORK_NAME = "trip-analysis"
    }
}
