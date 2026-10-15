package app.wend.sea.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
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

/** 크게 볼 사진 한 장. [takenAt] 은 촬영 시각(ms), 없으면 null */
data class ViewerItem(val media: MediaThumbnail, val takenAt: Long?)

data class ViewerUiState(
    val loaded: Boolean = false,
    val items: List<ViewerItem> = emptyList(),
    /** 처음 보여 줄 장(누른 사진) */
    val startIndex: Int = 0,
    val placeName: String = "",
    /** 장소의 날짜 순번(0부터) — 날짜 색 */
    val day: Int = 0,
)

sealed interface ViewerEvent {
    data class ShowMessage(val message: String) : ViewerEvent

    /** 여행에서 뺐다 — 화면을 닫고 상세에서 알린다 */
    data object Removed : ViewerEvent
}

/**
 * 사진 크게 보기(여행 상세에서). 그 장소의 사진을 옆으로 넘겨 본다.
 * 아래 버튼: 베스트샷으로 · 여행에서 빼기 · 공유 · 휴지통.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 네비게이션 인자(ViewerRoute) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])
    private val place: Int = checkNotNull(savedStateHandle["place"])
    private val index: Int = checkNotNull(savedStateHandle["index"])

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val p = detail.places.getOrNull(place) ?: return@launch
            val zone = ZoneId.systemDefault()
            fun dateOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val dates = detail.places.map { dateOf(it.start) }.distinct()
            _uiState.value = ViewerUiState(
                loaded = true,
                items = p.media.map { ViewerItem(MediaThumbnail(it.id, it.video), it.takenAt) },
                startIndex = index.coerceIn(0, (p.media.size - 1).coerceAtLeast(0)),
                placeName = detail.placeUserNames[place] ?: detail.placeCities[place] ?: "장소 ${place + 1}",
                day = dates.indexOf(dateOf(p.start)).coerceAtLeast(0),
            )
        }
    }

    fun onBestShot() {
        _events.trySend(ViewerEvent.ShowMessage("다음 단계(베스트샷 고르기)는 곧 이어서 만들어요"))
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
