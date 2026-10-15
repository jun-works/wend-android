package app.wend.sea.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.Trip
import app.wend.sea.data.geo.BaseMap
import app.wend.sea.data.geo.MapArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
) {
    val number: Int get() = index + 1
    val name: String get() = city ?: "장소 $number"
}

/** 하루. [day] 는 0부터, [ordinal] 은 여행 첫날부터 센 "N일째" */
data class DayUi(val day: Int, val ordinal: Int, val date: LocalDate, val places: List<PlaceUi>)

data class TripDetailUiState(
    val trip: Trip? = null,
    val days: List<DayUi> = emptyList(),
    /** 지도 평면 x = 경도 × lonScale (여행 평균 위도 기준) */
    val lonScale: Double = 1.0,
) {
    val places: List<PlaceUi> get() = days.flatMap { it.places }
}

sealed interface TripDetailEvent {
    data class ShowMessage(val message: String) : TripDetailEvent
}

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
    private val baseMap: BaseMap,
) : ViewModel() {

    /** 네비게이션 인자(TripRoute.number) */
    private val number: Int = checkNotNull(savedStateHandle["number"])

    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val (trip, places, placeCities) = analysisRepository.tripDetail(number) ?: return@launch
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
                )
            }
            val days = dates.mapIndexed { d, date ->
                DayUi(d, ChronoUnit.DAYS.between(firstDate, date).toInt() + 1, date, ui.filter { it.day == d })
            }
            _uiState.value = TripDetailUiState(trip, days, lonScale)
        }
    }

    /** 지도에 깔 오프라인 기본 지도(땅·바다·도로·경계·도시) */
    suspend fun mapArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): MapArea =
        baseMap.area(minLat, maxLat, minLon, maxLon)

    fun onBestShots() = comingSoon("베스트샷 고르기")
    fun onCleanUp() = comingSoon("사진 정리하기")
    fun onPhotoClick() = comingSoon("사진 크게 보기")

    private fun comingSoon(what: String) {
        _events.trySend(TripDetailEvent.ShowMessage("다음 단계($what)는 곧 이어서 만들어요"))
    }
}
