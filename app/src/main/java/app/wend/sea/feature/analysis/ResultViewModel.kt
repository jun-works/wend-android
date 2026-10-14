package app.wend.sea.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.Trip
import app.wend.sea.feature.globe.DEFAULT_NICKNAME
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
import javax.inject.Inject

data class ResultUiState(
    val nickname: String = DEFAULT_NICKNAME,
    val summary: AnalysisSummary? = null,
    /** 찍은 순서(오래된 것부터) */
    val trips: List<Trip> = emptyList(),
)

sealed interface ResultEvent {
    data class ShowMessage(val message: String) : ResultEvent

    /** 모든 사진을 허용해서 처음부터 다시 찾는다 */
    data object Reanalyze : ResultEvent

    /** 결과를 봤으니 목록(홈)으로 */
    data object OpenTrips : ResultEvent
}

/** 찾기 완료 · 여행 없음 화면이 같이 쓴다 */
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    private val resultHandler: PermissionResultHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<ResultEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var restarting = false

    init {
        viewModelScope.launch {
            val nickname = userRepository.nickname.firstOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_NICKNAME
            val summary = analysisRepository.summary.firstOrNull()
            _uiState.update { it.copy(nickname = nickname, summary = summary, trips = analysisRepository.trips()) }
        }
    }

    fun onBrowse() {
        viewModelScope.launch {
            userRepository.markAnalysisResultSeen()
            _events.send(ResultEvent.OpenTrips)
        }
    }

    fun onTripClick() {
        _events.trySend(ResultEvent.ShowMessage("다음 단계(여행 상세)는 곧 이어서 만들어요"))
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
        if (access == MediaAccess.All && _uiState.value.summary?.partial == true) restart()
    }

    private suspend fun restart() {
        if (restarting) return
        restarting = true
        analysisRepository.start()
        _events.send(ResultEvent.Reanalyze)
    }
}
