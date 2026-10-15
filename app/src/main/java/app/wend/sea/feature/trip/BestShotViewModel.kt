package app.wend.sea.feature.trip

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
import javax.inject.Inject

/** 같은 장면 한 줄. [key] 는 "장소:장면" */
data class BestSceneUi(val key: String, val start: Long, val photos: List<MediaThumbnail>)

/** 장면이 있는 장소 하나. [index] 는 여행 안의 장소 순번(0부터) — 번호는 상세와 같다 */
data class BestPlaceUi(val index: Int, val name: String, val scenes: List<BestSceneUi>) {
    val number: Int get() = index + 1
}

data class BestShotUiState(
    val loaded: Boolean = false,
    val places: List<BestPlaceUi> = emptyList(),
    /** 장면마다 고른 사진 */
    val picks: Map<String, MediaThumbnail> = emptyMap(),
    /** 아직 살펴보지 못한 사진이 있다(뒤에서 계산 중) */
    val pending: Boolean = false,
)

/**
 * 시안 ⑮ 베스트샷 고르기 — design/BEST_SHOT.md. 앱은 좋은 사진을 판정하지 않는다:
 * 같은 장면을 여러 번 찍은 사진끼리 한 줄로 보여 주고, 사용자가 장면마다 한 장을 고르거나 고르지 않는다.
 * 정하기 전에는 저장하지 않는다.
 */
@HiltViewModel
class BestShotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 네비게이션 인자(BestShotRoute.trip) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])

    private val _uiState = MutableStateFlow(BestShotUiState())
    val uiState: StateFlow<BestShotUiState> = _uiState.asStateFlow()

    /** 정했다 — 고른 장수 */
    private val _done = Channel<Int>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val scenes = analysisRepository.similarScenes(detail)
            val best = analysisRepository.bestShots()
            val places = scenes.places.mapIndexedNotNull { i, list ->
                if (list.isEmpty()) return@mapIndexedNotNull null
                BestPlaceUi(
                    index = i,
                    name = detail.placeUserNames[i] ?: detail.placeCities[i] ?: "장소 ${i + 1}",
                    scenes = list.mapIndexed { j, s -> BestSceneUi("$i:$j", s.start, s.photos) },
                )
            }
            // 이미 고른 베스트샷은 고른 채로 보여 준다
            val picks = places.flatMap { it.scenes }.mapNotNull { s -> s.photos.firstOrNull { it.mediaId in best }?.let { s.key to it } }.toMap()
            _uiState.value = BestShotUiState(loaded = true, places = places, picks = picks, pending = scenes.pending)
        }
    }

    /** 장면에서 한 장을 고른다. 고른 사진을 다시 누르면 그 장면은 고르지 않은 채로 */
    fun pick(scene: String, photo: MediaThumbnail) = _uiState.update { s ->
        s.copy(picks = if (s.picks[scene] == photo) s.picks - scene else s.picks + (scene to photo))
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val scope = state.places.flatMap { p -> p.scenes.flatMap { s -> s.photos.map { it.mediaId } } }
            analysisRepository.saveBestShots(scope, state.picks.values.map { it.mediaId })
            _done.send(state.picks.size)
        }
    }
}
