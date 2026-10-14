package app.wend.sea.feature.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.globe.DEFAULT_NICKNAME
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

data class GrantedUiState(
    val nickname: String = DEFAULT_NICKNAME,
    val photos: Int = 0,
    val videos: Int = 0,
    /** 위치가 남아 있는 사진·영상 수 — 파일을 읽는 동안 계속 늘어난다 */
    val withLocation: Int = 0,
)

sealed interface GrantedEvent {
    data class ShowMessage(val message: String) : GrantedEvent

    /** 그사이 설정에서 권한이 바뀌어 다른 결과 화면으로 */
    data class ShowResult(val access: MediaAccess) : GrantedEvent
}

@HiltViewModel
class GrantedViewModel @Inject constructor(
    userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GrantedUiState())
    val uiState: StateFlow<GrantedUiState> = _uiState.asStateFlow()

    private val _events = Channel<GrantedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val nickname = userRepository.nickname.firstOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_NICKNAME
            val counts = mediaRepository.counts()
            _uiState.update { it.copy(nickname = nickname, photos = counts.photos, videos = counts.videos) }
        }
        viewModelScope.launch {
            mediaRepository.locationCount().collect { found -> _uiState.update { it.copy(withLocation = found) } }
        }
    }

    private var leaving = false

    /** 화면에 돌아올 때마다: 설정에서 권한을 거뒀으면(앱이 종료됐다 복원된 경우 포함) 맞는 결과 화면으로 */
    fun checkAccess() {
        val access = mediaRepository.currentAccess()
        if (access != MediaAccess.All) leave(access)
    }

    private fun leave(access: MediaAccess) {
        if (leaving) return
        leaving = true
        _events.trySend(GrantedEvent.ShowResult(access))
    }

    /** 시작 직전에 권한을 다시 본다 — 화면을 띄운 뒤 설정에서 권한을 거둘 수 있다 */
    fun onStartAnalysis() {
        when (val access = mediaRepository.currentAccess()) {
            MediaAccess.All -> _events.trySend(GrantedEvent.ShowMessage("다음 단계(여행 찾기)는 곧 이어서 만들어요"))
            else -> leave(access)
        }
    }
}
