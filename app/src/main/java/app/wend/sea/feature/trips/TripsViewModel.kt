package app.wend.sea.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.Trip
import app.wend.sea.feature.permission.PermissionResultHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class TripsUiState(
    val loaded: Boolean = false,
    /** 찍은 순서(오래된 것부터) — 지구본 덩어리 순서와 같다 */
    val trips: List<Trip> = emptyList(),
    val covers: Map<Int, List<MediaThumbnail>> = emptyMap(),
    val summary: AnalysisSummary? = null,
) {
    val partial: Boolean get() = summary?.partial == true
    val includedCount: Int get() = trips.sumOf { it.mediaCount }

    /** 여행에 담기지 않은 사진·영상 수 */
    val leftCount: Int get() = ((summary?.total ?: 0) - includedCount).coerceAtLeast(0)

    /** 최신순 연도별 묶음 */
    val years: List<Pair<Int, List<Trip>>>
        get() = trips.reversed().groupBy { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).year }.toList()
}

sealed interface TripsEvent {
    data class ShowMessage(val message: String) : TripsEvent

    /** 모든 사진을 허용해서 처음부터 다시 찾는다 */
    data object Reanalyze : TripsEvent
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    private val resultHandler: PermissionResultHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var restarting = false

    init {
        viewModelScope.launch {
            val trips = analysisRepository.trips()
            _uiState.update {
                it.copy(
                    loaded = true,
                    trips = trips,
                    covers = analysisRepository.covers(),
                    summary = analysisRepository.summary.firstOrNull(),
                )
            }
        }
    }

    fun onLeftClick() = comingSoon("빠진 사진")
    fun onSettingsClick() = comingSoon("설정")

    private fun comingSoon(what: String) {
        _events.trySend(TripsEvent.ShowMessage("다음 단계($what)는 곧 이어서 만들어요"))
    }

    /** 권한 안내창 결과: 모두 허용이 되면 처음부터 다시 찾는다 */
    fun onRequestResult(access: MediaAccess, showRationale: Boolean) {
        viewModelScope.launch {
            resultHandler.record(access, showRationale)
            restartIfAllowedAll(access)
        }
    }

    /** 설정에서 돌아옴 · 화면 복귀 */
    fun checkAccess() {
        viewModelScope.launch { restartIfAllowedAll(mediaRepository.currentAccess()) }
    }

    private suspend fun restartIfAllowedAll(access: MediaAccess) {
        if (restarting || access != MediaAccess.All || !_uiState.value.partial) return
        restarting = true
        analysisRepository.start()
        _events.send(TripsEvent.Reanalyze)
    }
}
