package app.wend.sea.feature.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.globe.DEFAULT_NICKNAME
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermUiState(
    val nickname: String = DEFAULT_NICKNAME,
    /** 안내창이 더 뜨지 않아 버튼이 설정으로 보낸다 */
    val settingsOnly: Boolean = false,
)

/** 권한 결과에 따라 갈 화면 */
sealed interface PermissionEvent {
    data class ShowResult(val access: MediaAccess) : PermissionEvent
}

@HiltViewModel
class PermViewModel @Inject constructor(
    userRepository: UserRepository,
    private val resultHandler: PermissionResultHandler,
) : ViewModel() {

    val uiState: StateFlow<PermUiState> =
        combine(userRepository.nickname, userRepository.mediaSettingsOnly) { nickname, settingsOnly ->
            PermUiState(nickname?.takeIf { it.isNotBlank() } ?: DEFAULT_NICKNAME, settingsOnly)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermUiState())

    private val _events = Channel<PermissionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onRequestResult(access: MediaAccess, showRationale: Boolean) {
        viewModelScope.launch {
            resultHandler.record(access, showRationale)
            _events.send(PermissionEvent.ShowResult(access))
        }
    }

    /** 설정에서 돌아왔을 때: 허용했으면 결과 화면으로, 아니면 그대로 */
    fun onReturnFromSettings(access: MediaAccess) {
        if (access == MediaAccess.None) return
        viewModelScope.launch {
            resultHandler.record(access, showRationale = false)
            _events.send(PermissionEvent.ShowResult(access))
        }
    }
}
