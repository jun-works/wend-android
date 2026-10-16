package app.wend.sea.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.Trip
import app.wend.sea.data.geo.BaseMap
import android.content.Context
import app.wend.sea.data.geo.CityFinder
import app.wend.sea.data.geo.MapArea
import app.wend.sea.data.geo.MapCity
import app.wend.sea.data.geo.NatureMap
import app.wend.sea.data.geo.localName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import app.wend.sea.feature.analysis.autoName
import app.wend.sea.feature.analysis.name
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos

/** 장소 하나(화면용). [x]·[y] 는 지도 평면 좌표(경도·위도 비율 보정), 위치가 없으면 null */
data class PlaceUi(
    val index: Int,
    val day: Int,
    /** 여행 첫날부터 센 "N일째" */
    val dayOrdinal: Int,
    val start: Long,
    val end: Long,
    val count: Int,
    val photos: List<MediaThumbnail>,
    val x: Double?,
    val y: Double?,
    /** 앞서 들른 같은 곳의 순번 — 지도 번호는 그곳 하나만 */
    val sameAs: Int?,
    /** 사용자 언어 이름이 있는 가까운 도시(없으면 가장 가까운 도시 영어 이름). 위치가 없거나 근처에 도시가 없으면 null */
    val city: String?,
    /** 사용자가 바꾼 이름 */
    val userName: String? = null,
    /** 이 장소의 사진·영상 전부(이름을 바꿀 때 붙인다) */
    val media: List<Pair<Boolean, Long>> = emptyList(),
) {
    val number: Int get() = index + 1
    val autoName: String get() = city ?: "장소 $number"
    val name: String get() = userName ?: autoName
}

/** 같은 이름의 다른 장소가 있어서 같이 바꿀지 묻는 중 */
data class PendingRename(val index: Int, val newName: String, val oldName: String, val others: List<Int>)

/** 방금 이름이 바뀐 것들 — 한 번 같이 빛난다. [token] 이 바뀔 때마다 다시 */
data class RenameGlow(val places: Set<Int> = emptySet(), val title: Boolean = false, val token: Long = 0)

/** 하루. [day] 는 0부터, [ordinal] 은 여행 첫날부터 센 "N일째" */
data class DayUi(val day: Int, val ordinal: Int, val date: LocalDate, val places: List<PlaceUi>)

data class TripDetailUiState(
    val trip: Trip? = null,
    val days: List<DayUi> = emptyList(),
    /** 지도 평면 x = 경도 × lonScale (여행 평균 위도 기준) */
    val lonScale: Double = 1.0,
    val pending: PendingRename? = null,
    val glow: RenameGlow = RenameGlow(),
    /** 사용자가 고른 베스트샷(사진 id) — 사진에 별 */
    val best: Set<Long> = emptySet(),
) {
    val places: List<PlaceUi> get() = days.flatMap { it.places }
}

sealed interface TripDetailEvent {
    data class ShowMessage(val message: String) : TripDetailEvent

    /** 이름을 바꿨다 — 스낵바의 되돌리기로 [undo] */
    data class Renamed(val message: String, val undo: () -> Unit) : TripDetailEvent
}

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
    private val baseMap: BaseMap,
    private val natureMap: NatureMap,
    private val cityFinder: CityFinder,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val locale: Locale = context.resources.configuration.locales[0]

    /** 네비게이션 인자(TripRoute.number) */
    private val number: Int = checkNotNull(savedStateHandle["number"])

    companion object {
        /** 사진 크게 보기·베스트샷·사진 정리하기 화면이 돌려주는 결과(여행이 바뀜, 네비게이션 항목의 savedStateHandle). 빈 문자열이면 안내 없이 다시 읽기만 */
        const val VIEWER_RESULT = "viewerResult"
    }

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    /**
     * 크게 보기·베스트샷 화면에서 여행을 바꾸고 돌아왔다(빼기·휴지통·베스트샷) — 다시 읽고, 안내가 있으면 띄운다.
     * 결과는 네비게이션 항목의 savedStateHandle 로 오는데, 그건 이 ViewModel 의 SavedStateHandle 과 다른 객체라 NavHost 가 넘겨준다.
     */
    fun onResult(message: String) {
        load()
        if (message.isNotEmpty()) _events.trySend(TripDetailEvent.ShowMessage(message))
    }

    private fun load() {
        viewModelScope.launch {
            val (trip, places, placeCities, placeUserNames) = analysisRepository.tripDetail(number) ?: return@launch
            val zone = ZoneId.systemDefault()
            fun dateOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val firstDate = dateOf(trip.start)
            val dates = places.map { dateOf(it.start) }.distinct()
            val meanLat = places.mapNotNull { it.latitude }.average().takeIf { !it.isNaN() } ?: 0.0
            val lonScale = cos(meanLat * PI / 180)
            val ui = places.mapIndexed { i, p ->
                PlaceUi(
                    index = i,
                    day = dates.indexOf(dateOf(p.start)),
                    dayOrdinal = ChronoUnit.DAYS.between(firstDate, dateOf(p.start)).toInt() + 1,
                    start = p.start,
                    end = p.end,
                    count = p.media.size,
                    photos = p.media.take(6).map { MediaThumbnail(it.id, it.video) },
                    x = p.longitude?.let { it * lonScale },
                    y = p.latitude?.let { -it },
                    sameAs = p.sameAs,
                    city = placeCities[i],
                    userName = placeUserNames[i],
                    media = p.media.map { it.video to it.id },
                )
            }
            val days = dates.mapIndexed { d, date ->
                DayUi(d, ChronoUnit.DAYS.between(firstDate, date).toInt() + 1, date, ui.filter { it.day == d })
            }
            val best = analysisRepository.bestShots()
            _uiState.update { it.copy(trip = trip, days = days, lonScale = lonScale, best = best) }
        }
    }

    /** 지도에 깔 오프라인 기본 지도(땅·바다·도로·경계·도시) */
    suspend fun mapArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): MapArea {
        val area = baseMap.area(minLat, maxLat, minLon, maxLon)
        // 도시 글자는 장소 이름과 같은 도시 데이터에서(같은 GeoNames 도시), 사용자 언어로
        val cities = cityFinder.byIds(area.cities.map { it.geonameId }.filter { it > 0 })
        val labeled = area.cities.map { c ->
            val name = cities[c.geonameId]?.localName(locale) ?: if (locale.language == "ko") c.name else c.enName
            MapCity(c.latitude, c.longitude, c.rank, name, c.geonameId, c.enName)
        }
        // 기본 지도의 큰 도시만으로는 비는 곳(사막·시골)이 많아, 도시 데이터의 마을을 인구 순으로 더한다
        val known = area.cities.map { it.geonameId }.toSet()
        val towns = cityFinder.biggestIn(minLat, maxLat, minLon, maxLon, limit = 80)
            .filter { it.id !in known }
            .map { MapCity(it.lat / 1e4, it.lon / 1e4, rank = 20, name = it.localName(locale), geonameId = it.id, town = true) }
        val nature = natureMap.area(minLat, maxLat, minLon, maxLon, locale)
        return MapArea(area.shapes, labeled + towns, nature)
    }

    /** 여행 이름 바꾸기. 처음 이름과 같게 적으면 자동 이름으로 돌아간다 */
    fun renameTrip(name: String) {
        val trip = _uiState.value.trip ?: return
        if (name == trip.name) return
        val before = trip.userName
        setTripName(name.takeIf { it != trip.autoName })
        _events.trySend(TripDetailEvent.Renamed("여행 이름을 바꿨어요") { setTripName(before) })
    }

    private fun setTripName(userName: String?) {
        _uiState.update { s -> s.copy(trip = s.trip?.copy(userName = userName), glow = RenameGlow(title = true, token = s.glow.token + 1)) }
        viewModelScope.launch { analysisRepository.renameTrip(number, userName) }
    }

    /** 장소 이름 바꾸기. 지금 같은 이름인 다른 장소가 있으면 같이 바꿀지 먼저 묻는다 */
    fun renamePlace(index: Int, name: String) {
        val places = _uiState.value.places
        val place = places.getOrNull(index) ?: return
        if (name == place.name) return
        val others = places.filter { it.index != index && it.name == place.name }.map { it.index }
        if (others.isEmpty()) {
            applyPlaceNames(listOf(index), name)
        } else {
            _uiState.update { it.copy(pending = PendingRename(index, name, place.name, others)) }
        }
    }

    fun confirmRename(all: Boolean) {
        val pending = _uiState.value.pending ?: return
        _uiState.update { it.copy(pending = null) }
        applyPlaceNames(if (all) listOf(pending.index) + pending.others else listOf(pending.index), pending.newName)
    }

    fun cancelRename() = _uiState.update { it.copy(pending = null) }

    private fun applyPlaceNames(indices: List<Int>, name: String) {
        val before = indices.associateWith { i -> _uiState.value.places.first { it.index == i }.userName }
        setPlaceNames(indices.associateWith { name })
        val message = if (indices.size > 1) "장소 ${indices.size}곳의 이름을 바꿨어요" else "장소 이름을 바꿨어요"
        _events.trySend(TripDetailEvent.Renamed(message) { setPlaceNames(before, raw = true) })
    }

    /** [raw] 가 아니면 처음 이름과 같은 이름은 자동 이름으로 돌린다 */
    private fun setPlaceNames(names: Map<Int, String?>, raw: Boolean = false) {
        val changed = mutableListOf<PlaceUi>()
        _uiState.update { s ->
            s.copy(
                days = s.days.map { day ->
                    day.copy(
                        places = day.places.map { p ->
                            if (p.index !in names) return@map p
                            val name = names[p.index]
                            p.copy(userName = if (raw) name else name?.takeIf { it != p.autoName }).also { changed += it }
                        },
                    )
                },
                glow = RenameGlow(places = names.keys, token = s.glow.token + 1),
            )
        }
        viewModelScope.launch { changed.forEach { analysisRepository.renamePlace(it.media, it.userName) } }
    }

}
