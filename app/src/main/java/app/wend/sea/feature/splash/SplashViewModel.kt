package app.wend.sea.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 스플래시 다음에 갈 곳. 닉네임을 이미 정했으면 닉네임 입력을 건너뛴다. */
enum class SplashDestination { Nickname, Globe }

@HiltViewModel
class SplashViewModel @Inject constructor(
    userRepository: UserRepository,
) : ViewModel() {

    /** 저장소를 읽기 전에는 null */
    val destination: StateFlow<SplashDestination?> = userRepository.nickname
        .map { if (it.isNullOrBlank()) SplashDestination.Nickname else SplashDestination.Globe }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
