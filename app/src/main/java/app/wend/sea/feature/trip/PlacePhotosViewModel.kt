package app.wend.sea.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.core.designsystem.component.key
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.feature.analysis.name
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

/** 장소 사진 한 장. [takenAt] 은 촬영 시각(ms) */
data class PlacePhoto(val media: MediaThumbnail, val takenAt: Long?)

data class PlacePhotosUiState(
    val loaded: Boolean = false,
    val tripName: String = "",
    /** 여행 안의 장소 순번(0부터) — 크게 보기로 넘긴다 */
    val place: Int = 0,
    /** 장소의 날짜 순번(0부터) — 날짜 색 */
    val day: Int = 0,
    val dayOrdinal: Int = 1,
    val date: LocalDate? = null,
    val name: String = "",
    val start: Long = 0,
    val end: Long = 0,
    /** 촬영 순서대로 */
    val photos: List<PlacePhoto> = emptyList(),
    /** 사용자가 고른 베스트샷(사진 id) — 별 */
    val best: Set<Long> = emptySet(),
)

/**
 * 시안 ㉑ 장소 전체 사진: 여행 상세의 방문 장소 하나(같은 이름의 다른 날 방문은 합치지 않는다)의 사진 전부.
 * 크게 보기에서 빼거나 휴지통으로 옮기면 다시 읽는다.
 */
@HiltViewModel
class PlacePhotosViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 네비게이션 인자(PlacePhotosRoute) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])
    private val firstPlace: Int = checkNotNull(savedStateHandle["place"])

    private val _uiState = MutableStateFlow(PlacePhotosUiState())
    val uiState: StateFlow<PlacePhotosUiState> = _uiState.asStateFlow()

    private val _lastViewed = MutableStateFlow<Long?>(null)
    val lastViewed: StateFlow<Long?> = _lastViewed.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    /**
     * 크게 보기에서 돌아왔다. 결과는 네비게이션 항목의 savedStateHandle 로 오는데,
     * 그건 이 ViewModel 의 SavedStateHandle 과 다른 객체라 NavHost 가 넘겨준다.
     */
    fun onResult(message: String) {
        load()
        if (message.isNotEmpty()) _messages.trySend(message)
    }

    /** 크게 보기에서 마지막으로 본 사진 — 그 사진이 보이게 한다 */
    fun showLastViewed(key: Long) {
        _lastViewed.value = key
    }

    fun consumeLastViewed() {
        _lastViewed.value = null
    }

    private fun load() {
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val places = detail.places
            // 사진을 빼면 장소가 다시 묶여 순번이 바뀔 수 있다 — 보던 사진이 가장 많이 남은 장소를 따라간다
            val shown = _uiState.value.photos.map { it.media.key }.toSet()
            val place = if (shown.isEmpty()) {
                firstPlace
            } else {
                fun overlap(i: Int) = places[i].media.count { MediaThumbnail(it.id, it.video).key in shown }
                places.indices.filter { overlap(it) > 0 }.maxByOrNull(::overlap) ?: -1
            }
            val p = places.getOrNull(place)
            if (p == null) {
                // 이 장소의 사진이 모두 빠졌다 — 머리는 그대로 두고 빈 앨범
                _uiState.value = _uiState.value.copy(loaded = true, photos = emptyList())
                return@launch
            }
            val zone = ZoneId.systemDefault()
            fun dateOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val dates = places.map { dateOf(it.start) }.distinct()
            _uiState.value = PlacePhotosUiState(
                loaded = true,
                tripName = detail.trip.name,
                place = place,
                day = dates.indexOf(dateOf(p.start)).coerceAtLeast(0),
                dayOrdinal = ChronoUnit.DAYS.between(dateOf(detail.trip.start), dateOf(p.start)).toInt() + 1,
                date = dateOf(p.start),
                name = detail.placeUserNames[place] ?: detail.placeCities[place] ?: "장소 ${place + 1}",
                start = p.start,
                end = p.end,
                photos = p.media.map { PlacePhoto(MediaThumbnail(it.id, it.video), it.takenAt) },
                best = analysisRepository.bestShots(),
            )
        }
    }
}
