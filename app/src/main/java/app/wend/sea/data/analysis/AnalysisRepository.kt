package app.wend.sea.data.analysis

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.LibraryItem
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
import app.wend.sea.data.local.TripMediaEntity
import app.wend.sea.data.local.UserNameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

/** 여행에 넣기 후보: 여행과, 고른 사진과의 날짜 차이 */
data class TripChoice(val trip: Trip, val gapDays: Int?)

/** 여행 상세: 장소들과, 장소마다 자동 이름(가장 가까운 도시의 사용자 언어 이름, 없으면 null)과 사용자가 바꾼 이름 */
data class TripDetail(val trip: Trip, val places: List<Place>, val placeCities: List<String?>, val placeUserNames: List<String?>)

/** 같은 장면을 여러 번 찍은 사진 묶음 하나. [start] 는 첫 사진 촬영 시각 */
data class SimilarScene(val start: Long, val photos: List<MediaThumbnail>)

/** 장소마다 장면 묶음. [pending] 이면 아직 값을 계산하지 않은 사진이 있다(뒤에서 계산 중) */
data class SimilarScenes(val places: List<List<SimilarScene>>, val pending: Boolean)

/**
 * 사진 정리하기 후보(시안 ⑯). [similar] 는 베스트샷을 고른 장면의 나머지 사진, [dark] 는 너무 어두운 사진 — 한 장은 한 곳에만.
 * 베스트샷은 어느 쪽에도 들지 않는다. [hasBestShots] 는 이 여행에 고른 베스트샷이 있는지(없으면 비슷한 사진이 나올 수 없음).
 */
data class CleanUpCandidates(
    val similar: List<MediaThumbnail>,
    val dark: List<MediaThumbnail>,
    val pending: Boolean,
    val hasBestShots: Boolean,
)

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
        // 장수는 지금 보이는 사진으로 센다 — 휴지통에 옮긴 사진은 빠지고, 갤러리에서 되살리면 다시 들어간다
        val counts = visibleMedia().groupingBy { it.tripNumber }.eachCount()
        return entities.mapNotNull { e ->
            val count = counts[e.number] ?: return@mapNotNull null
            Trip(e.number, e.start, e.end, count, title(e, cities, locale), userNames[e.number])
        }
    }

    /**
     * 여행 상세는 장소 묶기·지명 찾기로 0.5초쯤 걸려서, 목록이 뜰 때 미리 읽어 두고(prefetch) 열 때 바로 쓴다.
     * 여행 행(장수·기간·도시)이나 보이는 사진(휴지통으로 옮김·되살림), 사용자 이름, 언어가 바뀌면 다시 계산한다.
     */
    private val detailLock = Mutex()
    private val detailCache = java.util.concurrent.ConcurrentHashMap<Int, CachedDetail>()
    private var namesVersion = 0

    private data class CachedDetail(
        val entity: TripEntity,
        val visible: Set<Pair<Boolean, Long>>,
        val namesVersion: Int,
        val locale: Locale,
        val detail: TripDetail,
    )

    /** 목록에 보이는 여행들의 상세를 미리 읽는다 */
    suspend fun prefetchTripDetails(numbers: List<Int>) = numbers.forEach { tripDetail(it) }

    /** 이미 읽어 둔 상세(기다리지 않는다). 화면 첫 그림용 — 바뀌었을 수 있어 [tripDetail] 로 다시 확인한다 */
    fun cachedTripDetail(number: Int): TripDetail? = detailCache[number]?.detail

    /** 여행 하나와, 그 사진들을 장소 단위로 나눈 것 */
    suspend fun tripDetail(number: Int): TripDetail? = detailLock.withLock {
        val entity = tripDao.trip(number) ?: return@withLock null
        val locale = locale()
        val visible = mediaRepository.visible(tripDao.media(number).map { it.video to it.mediaId })
        detailCache[number]?.takeIf { it.entity == entity && it.visible == visible && it.namesVersion == namesVersion && it.locale == locale }
            ?.let { return@withLock it.detail }
        loadTripDetail(entity).also { if (it != null) detailCache[number] = CachedDetail(entity, visible, namesVersion, locale, it) }
    }

    private suspend fun loadTripDetail(entity: TripEntity): TripDetail? {
        val number = entity.number
        val keys = tripDao.media(number).map { it.video to it.mediaId }
        // 휴지통에 있는 사진은 여기서 빠진다(여행에서 지우지는 않음 — 되살리면 돌아온다)
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
     * 묶음이 없는 장소는 빈 목록. 값을 아직 계산하지 않은 사진은 빠지고 [SimilarScenes.pending] 이 참.
     */
    suspend fun similarScenes(detail: TripDetail): SimilarScenes {
        val photoIds = detail.places.flatMap { p -> p.media.filter { !it.video }.map { it.id } }
        val hashes = featureDao.features(photoIds).associate { it.mediaId to it.hash }
        val grouper = SceneGrouper()
        val places = withContext(Dispatchers.Default) {
            detail.places.map { place ->
                val shots = place.media.mapNotNull { m ->
                    val hash = hashes[m.id]?.takeIf { !m.video } ?: return@mapNotNull null
                    SceneShot(m.id, m.takenAt ?: return@mapNotNull null, hash)
                }
                grouper.group(shots).map { scene -> SimilarScene(scene.first().takenAt, scene.map { MediaThumbnail(it.id, video = false) }) }
            }
        }
        return SimilarScenes(places, pending = photoIds.any { it !in hashes })
    }

    /** 사진 정리하기 후보 — 앱이 좋은 사진을 고르지 않으므로 비슷한 사진은 사용자가 베스트샷을 고른 장면에서만 */
    suspend fun cleanUpCandidates(detail: TripDetail): CleanUpCandidates {
        val scenes = similarScenes(detail)
        val best = bestShots()
        val similar = scenes.places.flatten()
            .filter { s -> s.photos.any { it.mediaId in best } }
            .flatMap { s -> s.photos.filter { it.mediaId !in best } }
        val photoIds = detail.places.flatMap { p -> p.media.filter { !it.video }.map { it.id } }
        val taken = similar.map { it.mediaId }.toSet()
        val features = featureDao.features(photoIds).associateBy { it.mediaId }
        val dark = photoIds.filter { id ->
            id !in best && id !in taken && features[id]?.let { PhotoQuality.tooDark(it.brightness, it.darkClip, it.peak) } == true
        }.map { MediaThumbnail(it, video = false) }
        return CleanUpCandidates(similar, dark, scenes.pending, photoIds.any { it in best })
    }

    /** 사용자가 고른 베스트샷(사진 id) */
    suspend fun bestShots(): Set<Long> = tripDao.bestShots().toSet()

    /** [scope] 사진들(베스트샷 화면에 보인 장면들)의 베스트샷을 [picks] 로 바꾼다 */
    suspend fun saveBestShots(scope: List<Long>, picks: List<Long>) = tripDao.replaceBestShots(scope, picks)

    /** 크게 보기에서 고른 한 장 — 그 사진이 속한 장면의 베스트샷을 바꾼다. 장면에 속하지 않으면 그 사진만 */
    suspend fun setBestShot(number: Int, media: MediaThumbnail) {
        if (media.video) return
        val detail = tripDetail(number) ?: return
        val scene = similarScenes(detail).places.flatten().firstOrNull { s -> s.photos.any { it == media } }
        tripDao.replaceBestShots(scene?.photos?.map { it.mediaId } ?: listOf(media.mediaId), listOf(media.mediaId))
    }

    /** 사진 한 장을 여행에서 뺀다. 빠진 사진에 남는다 */
    suspend fun excludeFromTrip(number: Int, media: MediaThumbnail) =
        tripDao.removeFromTrip(number, media.mediaId, media.video, exclude = true)

    /** 여행에 담기지 않은 사진·영상 전체(캡처 포함). 촬영 시각이 있는 것을 최신순으로, 그 뒤에 촬영 시각이 없는 것 */
    suspend fun leftMedia(): List<LibraryItem> {
        val inTrips = tripDao.allMedia().map { it.video to it.mediaId }.toSet()
        return mediaRepository.library().filter { (it.media.video to it.media.mediaId) !in inTrips }
            .sortedWith(compareByDescending<LibraryItem> { it.dated }.thenByDescending { it.time })
    }

    /**
     * 고른 사진과 기간이 가까운 여행부터 — [TripChoice.gapDays] 는 여행 기간과 사진 날짜 사이 날 수(겹치면 0).
     * [times] 가 비면(촬영 시각이 없는 사진만 고름) 가까운지 알 수 없어 최근 여행부터, 날짜 차이 없이.
     */
    suspend fun tripChoices(times: List<Long>): List<TripChoice> {
        if (times.isEmpty()) return trips().sortedByDescending { it.start }.map { TripChoice(it, null) }
        val zone = ZoneId.systemDefault()
        fun day(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val first = day(times.min())
        val last = day(times.max())
        return trips().map { t ->
            val s = day(t.start)
            val e = day(t.end)
            val gap = when {
                last < s -> java.time.temporal.ChronoUnit.DAYS.between(last, s)
                first > e -> java.time.temporal.ChronoUnit.DAYS.between(e, first)
                else -> 0L
            }
            TripChoice(t, gap.toInt())
        }.sortedBy { it.gapDays ?: 0 }
    }

    /** 새 여행 이름 제안: 사진에 위치가 있으면 가장 많이 찍은 도시, 없으면 null(화면이 날짜로 짓는다) */
    suspend fun suggestTripName(media: List<MediaThumbnail>): String? {
        val locale = locale()
        return mediaRepository.points(media.map { it.video to it.mediaId })
            .mapNotNull { p -> if (p.latitude == null || p.longitude == null) null else cityFinder.nearestCity(p.latitude, p.longitude, locale)?.name }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** 빠진 사진을 [number] 여행에 넣는다. 뺐던 사진이면 뺀 기록을 지운다 */
    suspend fun addToTrip(number: Int, media: List<MediaThumbnail>) {
        val anchor = tripDao.media(number).firstOrNull()?.let { it.video to it.mediaId } ?: return
        media.forEach { tripDao.unexclude(it.mediaId, it.video) }
        rebuildTrips(media.map { ManualPin(it.video to it.mediaId, anchor) })
    }

    /** 빠진 사진으로 새 여행을 만든다. 만든 여행 번호, 촬영 시각이 있는 사진이 없어 만들 수 없으면 null */
    suspend fun newTrip(media: List<MediaThumbnail>, name: String?): Int? {
        val anchor = media.firstOrNull()?.let { it.video to it.mediaId } ?: return null
        media.forEach { tripDao.unexclude(it.mediaId, it.video) }
        rebuildTrips(media.map { ManualPin(it.video to it.mediaId, anchor) })
        val number = tripDao.allMedia().firstOrNull { (it.video to it.mediaId) == anchor }?.tripNumber ?: return null
        if (!name.isNullOrBlank()) renameTrip(number, name.trim())
        return number
    }

    /**
     * 지금 여행들에 빠진 사진 [pins] 를 얹고 번호를 시작 순서로 다시 매긴다(다시 찾지 않음).
     * 사진이 그대로인 여행은 도시도 그대로 쓰고, 바뀐 여행만 다시 고른다.
     */
    private suspend fun rebuildTrips(pins: List<ManualPin>) {
        val entities = tripDao.trips()
        val media = tripDao.allMedia().groupBy { it.tripNumber }
        val keys = (media.values.flatten().map { it.video to it.mediaId } + pins.map { it.key }).distinct()
        val points = mediaRepository.points(keys).associateBy { it.video to it.id }
        // 휴지통에 있는 사진은 보이지 않아도 여행에 남긴다(되살리면 돌아오게) — 시각 없이 제자리에
        val current = entities.map { e ->
            val members = media[e.number].orEmpty().map { m -> points[m.video to m.mediaId] ?: MediaPoint(m.mediaId, m.video, null, null, null) }
            DetectedTrip(e.start, e.end, members)
        }
        val trips = withContext(Dispatchers.Default) { ManualTrips.apply(current, pins, points) }
        val before = entities.associateBy { e -> media[e.number].orEmpty().map { it.video to it.mediaId }.toSet() }
        val rows = trips.mapIndexed { i, t ->
            val old = before[t.media.map { it.video to it.id }.toSet()]
            if (old != null) {
                old.copy(number = i + 1, start = t.start, end = t.end, mediaCount = t.media.size)
            } else {
                val city = cityPicker.pick(t.media)
                TripEntity(i + 1, t.start, t.end, t.media.size, city.cityId, city.share, city.countryCode)
            }
        }
        tripDao.replaceAll(rows, trips.flatMapIndexed { i, t -> t.media.map { TripMediaEntity(i + 1, it.id, it.video) } })
        userRepository.analysisSummary.first()?.let { s ->
            userRepository.saveAnalysisSummary(s.copy(included = trips.sumOf { it.media.size }, tripCount = trips.size))
        }
    }

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

    /** 여행마다 카드에 보일 사진 3장: 고른 베스트샷 먼저, 그다음 첫 장 · 가운데 장 · 마지막 장 */
    suspend fun covers(): Map<Int, List<MediaThumbnail>> {
        val best = tripDao.bestShots().toSet()
        return visibleMedia().groupBy { it.tripNumber }.mapValues { (_, media) ->
            val auto = listOf(0, media.size / 2, media.lastIndex).distinct().filter { it in media.indices }.map { media[it] }
            // 사용자가 고른 베스트샷이 대표 사진 — 모자라면 첫 장 · 가운데 장 · 마지막 장으로 채운다
            (media.filter { !it.video && it.mediaId in best } + auto).distinct().take(3)
                .map { MediaThumbnail(it.mediaId, it.video) }
        }
    }

    /** 여행에 담긴 사진 가운데 지금 보이는 것(휴지통에 있는 것은 빠짐) */
    private suspend fun visibleMedia(): List<TripMediaEntity> {
        val all = tripDao.allMedia()
        val visible = mediaRepository.visible(all.map { it.video to it.mediaId })
        return all.filter { (it.video to it.mediaId) in visible }
    }

    private companion object {
        const val WORK_NAME = "trip-analysis"
    }
}
