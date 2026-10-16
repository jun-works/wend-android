package app.wend.sea.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.TripChoice
import app.wend.sea.data.analysis.TripDetail
import app.wend.sea.feature.analysis.name
import app.wend.sea.feature.left.NewTripUi
import app.wend.sea.feature.left.dateTripName
import app.wend.sea.feature.left.rangeText
import app.wend.sea.feature.left.tripPhrase
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
    /** 사용자가 고른 베스트샷(사진 id) */
    val best: Set<Long> = emptySet(),
    /** 빠진 사진에서 열었다 — 아래 버튼이 여행에 넣기 · 새 여행 · 공유 · 휴지통 */
    val left: Boolean = false,
    /** 여행에 넣기 시트(가까운 여행부터), 닫혀 있으면 null */
    val choices: List<TripChoice>? = null,
    val newTrip: NewTripUi? = null,
)

sealed interface ViewerEvent {
    data class ShowMessage(val message: String) : ViewerEvent

    /** 여행에서 뺐다 — 화면을 닫고 상세에서 알린다 */
    data object Removed : ViewerEvent

    /** 베스트샷을 바꿨다 — 상세·장소 사진이 별을 다시 그린다 */
    data class BestChanged(val message: String) : ViewerEvent

    /** 빠진 사진을 여행에 넣었다 — 화면을 닫고 빠진 사진에서 알린다 */
    data class Moved(val message: String) : ViewerEvent
}

/** 크게 보기를 연 곳: 여행 [trip] 의 장소 [place] 에서 [index] 번째 사진, [left] 면 빠진 사진 전체의 [index] 번째 */
data class ViewerArgs(val trip: Int, val place: Int, val index: Int, val left: Boolean = false)

/**
 * 사진 크게 보기. 연 화면 위에 겹쳐 열린다([open] · [close]) — 끌어내리는 동안 아래 화면이 비친다.
 * 여행 상세·장소 사진에서는 그 장소의 사진을, 빠진 사진에서는 빠진 사진 전체를 옆으로 넘겨 본다.
 * 아래 버튼은 들어온 곳에 따라: 베스트샷으로 · 여행에서 빼기 · 공유 · 휴지통 / 여행에 넣기 · 새 여행 · 공유 · 휴지통.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val analysisRepository: AnalysisRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    /** 지금 열린 곳 — 닫혀 있으면 null */
    private val _args = MutableStateFlow<ViewerArgs?>(null)
    val args: StateFlow<ViewerArgs?> = _args.asStateFlow()

    private val trip: Int get() = _args.value?.trip ?: 0
    private val place: Int get() = _args.value?.place ?: 0
    private val index: Int get() = _args.value?.index ?: 0

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loading: kotlinx.coroutines.Job? = null

    fun open(args: ViewerArgs) {
        loading?.cancel()
        _uiState.value = ViewerUiState()
        _args.value = args
        if (args.left) loadLeft() else loadTrip()
    }

    fun close() {
        loading?.cancel()
        _args.value = null
        _uiState.value = ViewerUiState()
    }

    /** 빠진 사진의 촬영 시각(없는 것은 빠짐) — 가까운 여행·새 여행 날짜에 쓴다 */
    private var leftTimes: Map<MediaThumbnail, Long> = emptyMap()

    private fun loadLeft() {
        loading = viewModelScope.launch {
            val items = analysisRepository.leftMedia()
            leftTimes = items.filter { it.dated }.associate { it.media to it.time }
            val ratios = mediaRepository.aspectRatios(items.map { it.media.video to it.media.mediaId })
            _uiState.value = ViewerUiState(
                loaded = true,
                items = items.map { ViewerItem(it.media, it.time.takeIf { _ -> it.dated }, ratios[it.media.video to it.media.mediaId]) },
                startIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                left = true,
            )
        }
    }

    private fun loadTrip() {
        // 누르자마자 사진이 보이게 상세가 읽어 둔 것으로 먼저 채운다
        analysisRepository.cachedTripDetail(trip)?.let { build(it, emptyMap()) }?.let { _uiState.value = it }
        loading = viewModelScope.launch {
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

    /** 빠진 사진 한 장을 넣을 여행 — 가까운 여행부터 */
    fun askAdd(item: ViewerItem) {
        viewModelScope.launch {
            val choices = analysisRepository.tripChoices(listOfNotNull(leftTimes[item.media]))
            _uiState.update { it.copy(choices = choices) }
        }
    }

    fun askNew(item: ViewerItem) {
        viewModelScope.launch {
            val time = leftTimes[item.media] ?: run {
                _events.send(ViewerEvent.ShowMessage("찍은 날짜가 있는 사진이어야 여행을 만들 수 있어요"))
                return@launch
            }
            val suggested = analysisRepository.suggestTripName(listOf(item.media)) ?: dateTripName(listOf(time))
            _uiState.update { it.copy(newTrip = NewTripUi("${rangeText(listOf(time))} · 1장", suggested)) }
        }
    }

    fun dismissPickers() = _uiState.update { it.copy(choices = null, newTrip = null) }

    fun addTo(item: ViewerItem, choice: TripChoice) {
        _uiState.update { it.copy(choices = null) }
        viewModelScope.launch {
            analysisRepository.addToTrip(choice.trip.number, listOf(item.media))
            _events.send(ViewerEvent.Moved("1장을 ${tripPhrase(choice.trip.name)}에 넣었어요"))
        }
    }

    fun makeTrip(item: ViewerItem, name: String) {
        _uiState.update { it.copy(newTrip = null) }
        viewModelScope.launch {
            val made = analysisRepository.newTrip(listOf(item.media), name) != null
            if (made) {
                _events.send(ViewerEvent.Moved("‘$name’ 여행을 만들었어요. 여행 목록에서 볼 수 있어요"))
            } else {
                _events.send(ViewerEvent.ShowMessage("찍은 날짜가 있는 사진이어야 여행을 만들 수 있어요"))
            }
        }
    }

    /** 여행에서 빼기: 빠진 사진에 남는다 */
    fun onRemoveFromTrip(item: ViewerItem) {
        viewModelScope.launch {
            analysisRepository.excludeFromTrip(trip, item.media)
            _events.send(ViewerEvent.Removed)
        }
    }

    /**
     * 시스템 확인창에서 허용된 뒤: 보던 목록에서 뺀다. 여행에서는 지우지 않는다 —
     * 휴지통에 있는 동안은 보이지 않아 저절로 빠지고, 갤러리에서 되살리면 원래 여행으로 돌아온다
     */
    fun onTrashed(item: ViewerItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(items = it.items - item) }
            _events.send(ViewerEvent.ShowMessage("1장을 휴지통으로 옮겼어요. 30일 안에 갤러리에서 되살릴 수 있어요"))
        }
    }
}
