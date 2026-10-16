package app.wend.sea.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.data.analysis.AnalysisPhase
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.AnalysisState
import app.wend.sea.data.analysis.Trip
import app.wend.sea.feature.globe.defaultNickname
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyzeUiState(
    val nickname: String = defaultNickname,
    /** 분석할 사진·영상 수(캡처 제외) */
    val total: Int = 0,
    /**
     * 실제 분석이 어디까지 왔는지를 화면 진행(0–1)의 상한으로 바꾼 값.
     * 시간 읽기 0.3 · 장소 0.3–0.55 · 묶기 0.55 · 끝 1.
     */
    val reachable: Float = 0f,
    /** 분석이 끝나야 채워진다 */
    val trips: List<Trip>? = null,
    val partial: Boolean = false,
    /** 분석 실패(그사이 권한을 거둔 경우 등) — 지금 접근 상태 */
    val failedWith: MediaAccess? = null,
)

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyzeUiState())
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val nickname = userRepository.nickname.firstOrNull()?.takeIf { it.isNotBlank() } ?: defaultNickname
            val total = mediaRepository.analysisItemCount()
            _uiState.update {
                it.copy(nickname = nickname, total = total, partial = mediaRepository.currentAccess() == MediaAccess.Partial)
            }
        }
        viewModelScope.launch {
            analysisRepository.state.collect { state ->
                when (state) {
                    is AnalysisState.Running -> _uiState.update { it.copy(reachable = state.reachable()) }
                    AnalysisState.Finished -> {
                        val trips = analysisRepository.trips()
                        _uiState.update { it.copy(reachable = 1f, trips = trips) }
                    }
                    AnalysisState.Failed -> _uiState.update { it.copy(failedWith = mediaRepository.currentAccess()) }
                    AnalysisState.Idle -> Unit
                }
            }
        }
    }

    private fun AnalysisState.Running.reachable(): Float = when (phase) {
        AnalysisPhase.Reading -> .3f
        AnalysisPhase.Places -> .3f + .25f * (if (total > 0) done.toFloat() / total else 1f)
        AnalysisPhase.Grouping -> .55f
    }
}
