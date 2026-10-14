package app.wend.sea.feature.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.analysis.AnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 일부 허용 화면에서 진하게 칠하는 방울 수 — 고른 수와 상관없이 시안 값으로 고정 */
const val PARTIAL_LIT_DOTS = 14

sealed interface PartialEvent {
    data class ShowResult(val access: MediaAccess) : PartialEvent
    data class ShowMessage(val message: String) : PartialEvent

    /** 권한 확인을 통과 — 알림 권한을 물은 뒤 [PartialViewModel.startAnalysis] */
    data object ReadyToStart : PartialEvent
}

@HiltViewModel
class PartialViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val resultHandler: PermissionResultHandler,
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 사용자가 고른 사진·영상 수 */
    private val _picked = MutableStateFlow(0)
    val picked: StateFlow<Int> = _picked.asStateFlow()

    private val _events = Channel<PartialEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 다른 결과 화면으로 떠나는 중 — 안내창 결과와 화면 복귀가 겹쳐도 한 번만 이동 */
    private var leaving = false

    /** 화면에 돌아올 때마다: 고른 사진이 바뀌었을 수 있고, 시스템에서 권한이 바뀌었을 수도 있다 */
    fun refresh() {
        viewModelScope.launch {
            when (val access = mediaRepository.currentAccess()) {
                MediaAccess.Partial -> _picked.value = mediaRepository.counts().let { it.photos + it.videos }
                else -> leave(access)
            }
        }
    }

    fun onRequestResult(access: MediaAccess, showRationale: Boolean) {
        viewModelScope.launch {
            resultHandler.record(access, showRationale)
            refresh()
        }
    }

    private suspend fun leave(access: MediaAccess) {
        if (leaving) return
        leaving = true
        _events.send(PartialEvent.ShowResult(access))
    }

    fun startAnalysis() = analysisRepository.start()

    /** 시작 직전에 권한을 다시 본다 — 화면을 띄운 뒤 설정에서 권한을 바꾸거나 거둘 수 있다 */
    fun onStartWithPicked() {
        viewModelScope.launch {
            when (val access = mediaRepository.currentAccess()) {
                MediaAccess.Partial -> _events.send(PartialEvent.ReadyToStart)
                else -> leave(access)
            }
        }
    }
}
