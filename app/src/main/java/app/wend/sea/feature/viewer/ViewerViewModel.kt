package app.wend.sea.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.TripDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** 크게 볼 사진 한 장. [takenAt] 은 촬영 시각(ms), 없으면 null. [ratio] 는 가로/세로(모르면 null) */
data class ViewerItem(val media: MediaThumbnail, val takenAt: Long?, val ratio: Float? = null)

data class ViewerUiState(
    val loaded: Boolean = false,
    val items: List<ViewerItem> = emptyList(),
    /** 처음 보여 줄 장(누른 사진) */
    val startIndex: Int = 0,
    val placeName: String = "",
    /** 장소의 날짜 순번(0부터) — 날짜 색 */
    val day: Int = 0,
    /** 장소 사진 화면에서 열었는지(사진이 날아올 썸네일이 그 화면에 있다) */
    val album: Boolean = false,
    /** 사용자가 고른 베스트샷(사진 id) */
    val best: Set<Long> = emptySet(),
)

sealed interface ViewerEvent {
    data class ShowMessage(val message: String) : ViewerEvent

    /** 여행에서 뺐다 — 화면을 닫고 상세에서 알린다 */
    data object Removed : ViewerEvent

    /** 베스트샷을 바꿨다 — 상세·장소 사진이 별을 다시 그린다 */
    data class BestChanged(val message: String) : ViewerEvent
}

/**
 * 사진 크게 보기(여행 상세에서). 그 장소의 사진을 옆으로 넘겨 본다.
 * 아래 버튼: 베스트샷으로 · 여행에서 빼기 · 공유 · 휴지통.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    /** 네비게이션 인자(ViewerRoute) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])
    private val place: Int = checkNotNull(savedStateHandle["place"])
    private val index: Int = checkNotNull(savedStateHandle["index"])
    private val album: Boolean = savedStateHandle["album"] ?: false

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // 썸네일에서 사진이 날아오려면 첫 그림부터 사진이 있어야 한다 — 상세가 읽어 둔 것으로 바로 채운다
        analysisRepository.cachedTripDetail(trip)?.let { build(it, emptyMap()) }?.let { _uiState.value = it }
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val p = detail.places.getOrNull(place) ?: return@launch
            val ratios = mediaRepository.aspectRatios(p.media.map { it.video to it.id })
            val best = analysisRepository.bestShots()
            build(detail, ratios)?.let { _uiState.value = it.copy(best = best) }
        }
    }

    private fun build(detail: TripDetail, ratios: Map<Pair<Boolean, Long>, Float>): ViewerUiState? {
        val p = detail.places.getOrNull(place) ?: return null
        val zone = ZoneId.systemDefault()
        fun dateOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val dates = detail.places.map { dateOf(it.start) }.distinct()
        return ViewerUiState(
            loaded = true,
            items = p.media.map { ViewerItem(MediaThumbnail(it.id, it.video), it.takenAt, ratios[it.video to it.id]) },
            startIndex = index.coerceIn(0, (p.media.size - 1).coerceAtLeast(0)),
            placeName = detail.placeUserNames[place] ?: detail.placeCities[place] ?: "장소 ${place + 1}",
            day = dates.indexOf(dateOf(p.start)).coerceAtLeast(0),
            album = album,
        )
    }

    /** 이 사진을 베스트샷으로 — 같은 장면에서 고른 다른 사진은 내려온다 */
    fun onBestShot(item: ViewerItem) {
        if (item.media.video) {
            _events.trySend(ViewerEvent.ShowMessage("영상은 베스트샷으로 고를 수 없어요"))
            return
        }
        if (item.media.mediaId in _uiState.value.best) return
        viewModelScope.launch {
            analysisRepository.setBestShot(trip, item.media)
            val best = analysisRepository.bestShots()
            _uiState.update { it.copy(best = best) }
            _events.send(ViewerEvent.BestChanged("이 사진을 ${_uiState.value.placeName}의 베스트샷으로 골랐어요"))
        }
    }

    /** 여행에서 빼기: 다시 분석해도 넣지 않고, 빠진 사진에 남는다 */
    fun onRemoveFromTrip(item: ViewerItem) {
        viewModelScope.launch {
            analysisRepository.excludeFromTrip(trip, item.media)
            _events.send(ViewerEvent.Removed)
        }
    }

    /** 시스템 확인창에서 허용된 뒤: 여행에서 덜어 내고 보던 목록에서도 뺀다 */
    fun onTrashed(item: ViewerItem) {
        viewModelScope.launch {
            analysisRepository.removeTrashed(trip, item.media)
            _uiState.update { it.copy(items = it.items - item) }
            _events.send(ViewerEvent.ShowMessage("1장을 휴지통으로 옮겼어요. 30일 안에 갤러리에서 되살릴 수 있어요"))
        }
    }
}
