package app.wend.sea.feature.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeniedViewModel @Inject constructor(
    userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val resultHandler: PermissionResultHandler,
) : ViewModel() {

    /** 두 번 거절해서 안내창이 더 뜨지 않음 — 설정 경로를 보여 주고 버튼이 설정으로 보낸다 */
    val settingsOnly: StateFlow<Boolean> = userRepository.mediaSettingsOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = Channel<PermissionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 안내창 결과와 화면 복귀가 겹쳐도 한 번만 이동 */
    private var leaving = false

    fun onRequestResult(access: MediaAccess, showRationale: Boolean) {
        viewModelScope.launch {
            resultHandler.record(access, showRationale)
            if (access != MediaAccess.None) leave(access)
        }
    }

    fun onReturnFromSettings(access: MediaAccess) {
        if (access == MediaAccess.None) return
        viewModelScope.launch {
            resultHandler.record(access, showRationale = false)
            leave(access)
        }
    }

    /** 화면에 돌아올 때마다: 이 앱 버튼을 거치지 않고 설정에서 허용했을 수도 있다 */
    fun checkAccess() {
        val access = mediaRepository.currentAccess()
        if (access == MediaAccess.None) return
        viewModelScope.launch {
            resultHandler.record(access, showRationale = false)
            leave(access)
        }
    }

    private suspend fun leave(access: MediaAccess) {
        if (leaving) return
        leaving = true
        _events.send(PermissionEvent.ShowResult(access))
    }
}
