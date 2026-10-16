package app.wend.sea.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.DataWiper
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.data.analysis.AnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loaded: Boolean = false,
    val nickname: String = "",
    val access: MediaAccess = MediaAccess.None,
    /** 사진 속 위치(ACCESS_MEDIA_LOCATION)를 읽을 수 있는지 */
    val location: Boolean = false,
    /** 볼 수 있는 사진·영상 수 */
    val mediaCount: Int = 0,
    val tripCount: Int = 0,
)

sealed interface SettingsEvent {
    /** 처음부터 다시 찾기 시작 — 분석 화면으로 */
    data object Rescan : SettingsEvent

    /** Wend 데이터를 지웠다 — 처음 화면으로 */
    data object Wiped : SettingsEvent
}

/**
 * 시안 ⑲ 설정. 일부러 조용하게: 앨범 지구본 + 이름, 사진 접근 상태, 처음부터 다시 찾기, 데이터 지우기, 정보.
 * 자동 찾기·새 여행 알림·개인정보 처리방침은 아직 넣지 않는다(2026-10-16 결정).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    private val dataWiper: DataWiper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 화면에 돌아올 때마다(Android 설정에서 권한을 바꾸고 올 수 있다) 다시 읽는다 */
    fun refresh() {
        viewModelScope.launch {
            val access = mediaRepository.currentAccess()
            _uiState.value = SettingsUiState(
                loaded = true,
                nickname = userRepository.nickname.first().orEmpty(),
                access = access,
                location = mediaRepository.canReadLocation(),
                mediaCount = if (access == MediaAccess.None) 0 else mediaRepository.visibleCount(),
                tripCount = analysisRepository.trips().size,
            )
        }
    }

    fun saveNickname(name: String) {
        viewModelScope.launch {
            userRepository.saveNickname(name)
            _uiState.update { it.copy(nickname = name) }
        }
    }

    fun rescan() {
        analysisRepository.start()
        _events.trySend(SettingsEvent.Rescan)
    }

    fun wipe() {
        viewModelScope.launch {
            dataWiper.wipe()
            _events.send(SettingsEvent.Wiped)
        }
    }
}
