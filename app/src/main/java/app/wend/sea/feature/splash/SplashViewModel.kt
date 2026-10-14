package app.wend.sea.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.AnalysisState
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

    /** 여행을 찾는 중(앱을 닫았다 다시 열었을 때) */
    Analyzing,

    /** 여행 찾기를 마쳤음 → 찾기 완료 / 여행 없음 */
    Found,
    NoFound,
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    userRepository: UserRepository,
    mediaRepository: MediaRepository,
    analysisRepository: AnalysisRepository,
) : ViewModel() {

    /** 저장소를 읽기 전에는 null */
    val destination: StateFlow<SplashDestination?> =
        combine(
            userRepository.nickname,
            userRepository.onboardingDone,
            analysisRepository.state,
            analysisRepository.summary,
        ) { nickname, onboarded, analysis, summary ->
            val access = mediaRepository.currentAccess()
            when {
                nickname.isNullOrBlank() -> SplashDestination.Nickname
                !onboarded -> SplashDestination.Globe
                access == MediaAccess.None -> SplashDestination.Permission
                analysis is AnalysisState.Running -> SplashDestination.Analyzing
                summary != null -> if (summary.tripCount > 0) SplashDestination.Found else SplashDestination.NoFound
                access == MediaAccess.All -> SplashDestination.Granted
                else -> SplashDestination.Partial
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
