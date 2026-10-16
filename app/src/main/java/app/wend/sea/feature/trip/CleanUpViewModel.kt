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

/** 정리 후보 한 장. [place] 는 장소 이름, [day] 는 날짜 순번(0부터, 색), [takenAt] 은 촬영 시각 — 크게 보기 위에 */
data class CleanPhotoUi(val media: MediaThumbnail, val place: String, val day: Int = 0, val takenAt: Long? = null)

/** 이유 하나(비슷한 사진 · 너무 어두운 사진) */
data class CleanGroupUi(val key: String, val title: String, val sub: String?, val photos: List<CleanPhotoUi>)

data class CleanUpUiState(
    val loaded: Boolean = false,
    val groups: List<CleanGroupUi> = emptyList(),
    /** 남기려고 눌러서 뺀 사진 — 나머지는 전부 체크된 채 */
    val kept: Set<MediaThumbnail> = emptySet(),
    /** 아직 살펴보지 못한 사진이 있다(뒤에서 계산 중) */
    val pending: Boolean = false,
    /** 이 여행에 고른 베스트샷이 있다 — 없으면 비슷한 사진이 나올 수 없어 빈 화면에서 알려 준다 */
    val hasBestShots: Boolean = false,
    /** 크게 보고 있는 이유(그룹 key)와 사진 순번, 닫혀 있으면 null */
    val viewing: Pair<String, Int>? = null,
) {
    val selected: List<MediaThumbnail>
        get() = groups.flatMap { g -> g.photos.map { it.media } }.filter { it !in kept }
}

/**
 * 시안 ⑯ 사진 정리하기. 정리 후보를 이유별로 보여 주고 전부 미리 체크한다 — 남길 사진만 눌러서 뺀다.
 * Wend 는 직접 지우지 않는다: 화면이 Android 시스템 확인창(MediaStore.createTrashRequest)을 띄운다.
 * 여행에서는 지우지 않는다 — 휴지통에 있는 동안 저절로 빠지고, 갤러리에서 되살리면 돌아온다. 흔들린 사진은 믿을 만한 판정 방법을 정하기 전까지 넣지 않는다.
 */
@HiltViewModel
class CleanUpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 네비게이션 인자(CleanUpRoute.trip) */
    private val trip: Int = checkNotNull(savedStateHandle["trip"])

    private val _uiState = MutableStateFlow(CleanUpUiState())
    val uiState: StateFlow<CleanUpUiState> = _uiState.asStateFlow()

    /** 휴지통으로 옮겼다 — 옮긴 장수 */
    private val _done = Channel<Int>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val detail = analysisRepository.tripDetail(trip) ?: return@launch
            val candidates = analysisRepository.cleanUpCandidates(detail)
            val zone = java.time.ZoneId.systemDefault()
            fun dateOf(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val dates = detail.places.map { dateOf(it.start) }.distinct()
            val info = detail.places.flatMapIndexed { i, p ->
                val name = detail.placeUserNames[i] ?: detail.placeCities[i] ?: "장소 ${i + 1}"
                val day = dates.indexOf(dateOf(p.start)).coerceAtLeast(0)
                p.media.filter { !it.video }.map { it.id to CleanPhotoUi(MediaThumbnail(it.id, video = false), name, day, it.takenAt) }
            }.toMap()
            fun photos(list: List<MediaThumbnail>) = list.map { info[it.mediaId] ?: CleanPhotoUi(it, "") }
            val groups = listOfNotNull(
                CleanGroupUi("similar", "비슷한 사진", "베스트샷과 거의 같은 사진", photos(candidates.similar)).takeIf { it.photos.isNotEmpty() },
                CleanGroupUi("dark", "너무 어두운 사진", null, photos(candidates.dark)).takeIf { it.photos.isNotEmpty() },
            )
            _uiState.value = CleanUpUiState(
                loaded = true,
                groups = groups,
                pending = candidates.pending,
                hasBestShots = candidates.hasBestShots,
            )
        }
    }

    /** 누르면 남기기(체크 해제), 다시 누르면 다시 정리 후보로 */
    fun toggle(photo: MediaThumbnail) = _uiState.update { s ->
        s.copy(kept = if (photo in s.kept) s.kept - photo else s.kept + photo)
    }

    /** 이유 [group] 의 [index] 번째 사진을 크게 본다. null 이면 닫는다 */
    fun view(group: String?, index: Int = 0) = _uiState.update { it.copy(viewing = group?.let { g -> g to index }) }

    /** 시스템 확인창에서 허용됨 — [trashed] 는 창을 띄울 때 넘긴 사진들 */
    fun onTrashed(trashed: List<MediaThumbnail>) {
        _done.trySend(trashed.size)
    }
}
