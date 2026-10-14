package app.wend.sea.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 스플래시 다음에 갈 곳. 이미 지난 온보딩 단계는 건너뛴다. */
enum class SplashDestination {
    Nickname,
    Globe,

    /** 온보딩을 마쳤고 아직 사진을 못 봄 → 사진 접근 안내 */
    Permission,

    /** 온보딩을 마쳤고 사진을 모두 볼 수 있음 → 앨범 열림 */
    Granted,

    /** 온보딩을 마쳤고 고른 사진만 볼 수 있음 → 일부 허용 */
    Partial,
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    userRepository: UserRepository,
    mediaRepository: MediaRepository,
) : ViewModel() {

    /** 저장소를 읽기 전에는 null */
    val destination: StateFlow<SplashDestination?> =
        combine(userRepository.nickname, userRepository.onboardingDone) { nickname, onboarded ->
            when {
                nickname.isNullOrBlank() -> SplashDestination.Nickname
                !onboarded -> SplashDestination.Globe
                else -> when (mediaRepository.currentAccess()) {
                    MediaAccess.All -> SplashDestination.Granted
                    MediaAccess.Partial -> SplashDestination.Partial
                    MediaAccess.None -> SplashDestination.Permission
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
