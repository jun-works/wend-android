package app.wend.sea.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.R
import app.wend.sea.core.text.AppText
import app.wend.sea.data.FavoriteRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.bestshot.BestShotSaveFlow
import app.wend.sea.feature.bestshot.BestShotSaveResult
import app.wend.sea.feature.bestshot.message
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

/** 장면이 있는 장소 하나. [index] 는 여행 안의 장소 순번(0부터) — 번호는 상세와 같다. [day] 는 날짜 순번(0부터, 색) */
data class BestPlaceUi(val index: Int, val name: String, val scenes: List<BestSceneUi>, val day: Int = 0) {
    val number: Int get() = index + 1
}

data class BestShotUiState(
    val loaded: Boolean = false,
    val hadPicks: Boolean = false,
    val places: List<BestPlaceUi> = emptyList(),
    /** 장면마다 고른 사진 */
    val picks: Map<String, MediaThumbnail> = emptyMap(),
    /** 아직 살펴보지 못한 사진이 있다(뒤에서 계산 중) */
    val pending: Boolean = false,
    /** 사진마다 촬영 시각 — 크게 보기 위에 */
    val takenAt: Map<MediaThumbnail, Long> = emptyMap(),
    /** 크게 보고 있는 장면과 사진 순번, 닫혀 있으면 null */
    val viewing: Pair<String, Int>? = null,
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
    userRepository: UserRepository,
    favoriteRepository: FavoriteRepository,
) : ViewModel() {

    /** 네비게이션 인자(BestShotRoute.trip) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])

    private val _uiState = MutableStateFlow(BestShotUiState())
    val uiState: StateFlow<BestShotUiState> = _uiState.asStateFlow()

    val saveFlow = BestShotSaveFlow(viewModelScope, userRepository.bestShotSaveMode, userRepository::setBestShotSaveMode, favoriteRepository::notFavoriteIds)
    private var originalIds = emptySet<Long>()
    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    /** 확정 및 시스템 승인 처리가 끝난 뒤 표시할 결과 */
    private val _done = Channel<String>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val scenes = analysisRepository.similarScenes(detail)
            val best = analysisRepository.bestShots()
            val zone = java.time.ZoneId.systemDefault()
            fun dateOf(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val dates = detail.places.map { dateOf(it.start) }.distinct()
            val places = scenes.places.mapIndexedNotNull { i, list ->
                if (list.isEmpty()) return@mapIndexedNotNull null
                BestPlaceUi(
                    index = i,
                    name = detail.placeUserNames[i] ?: detail.placeCities[i] ?: AppText.string(R.string.place_auto_name, i + 1),
                    scenes = list.mapIndexed { j, s -> BestSceneUi("$i:$j", s.start, s.photos) },
                    day = dates.indexOf(dateOf(detail.places[i].start)).coerceAtLeast(0),
                )
            }
            val takenAt = detail.places.flatMap { p -> p.media.mapNotNull { m -> m.takenAt?.let { MediaThumbnail(m.id, m.video) to it } } }.toMap()
            // 이미 고른 베스트샷은 고른 채로 보여 준다
            val picks = places.flatMap { it.scenes }.mapNotNull { s -> s.photos.firstOrNull { it.mediaId in best }?.let { s.key to it } }.toMap()
            originalIds = picks.values.map { it.mediaId }.toSet()
            _uiState.value = BestShotUiState(loaded = true, hadPicks = picks.isNotEmpty(), places = places, picks = picks, pending = scenes.pending, takenAt = takenAt)
        }
    }

    /** 장면에서 한 장을 고른다. 고른 사진을 다시 누르면 그 장면은 고르지 않은 채로 */
    fun pick(scene: String, photo: MediaThumbnail) = _uiState.update { s ->
        s.copy(picks = if (s.picks[scene] == photo) s.picks - scene else s.picks + (scene to photo))
    }

    /** 장면의 [index] 번째 사진을 크게 본다. null 이면 닫는다 */
    fun view(scene: String?, index: Int = 0) = _uiState.update { it.copy(viewing = scene?.let { s -> s to index }) }

    fun save() {
        val state = _uiState.value
        if (!state.loaded) return
        val picks = state.picks.values.map { it.mediaId }
        saveFlow.submit(
            ids = picks.filterNot { it in originalIds },
            save = {
                val scope = state.places.flatMap { p -> p.scenes.flatMap { s -> s.photos.map { it.mediaId } } }
                analysisRepository.saveBestShots(scope, picks)
            },
            onComplete = { result ->
                val text = result.message(
                    if (picks.isEmpty()) AppText.string(R.string.best_selection_cleared)
                    else AppText.plural(R.plurals.best_shots_picked, picks.size, picks.size),
                )
                if (result == BestShotSaveResult.SAVE_FAILED) _errors.send(text) else _done.send(text)
            },
        )
    }
}
