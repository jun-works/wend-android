package app.wend.sea.feature.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 앱의 실제 분류 로직 3단계 — 지구본이 단계마다 다르게 움직인다. */
enum class GlobeStep(val number: String, val label: String) {
    When("01", "언제 찍었나"),
    Where("02", "어디서 찍었나"),
    Group("03", "여행으로 묶기"),
}

data class GlobeUiState(
    val nickname: String = DEFAULT_NICKNAME,
    val step: GlobeStep = GlobeStep.When,
)

const val DEFAULT_NICKNAME = "여행자"

@HiltViewModel
class GlobeViewModel @Inject constructor(
    userRepository: UserRepository,
) : ViewModel() {

    private val step = MutableStateFlow(GlobeStep.When)
    private var autoJob: Job? = null

    val uiState: StateFlow<GlobeUiState> =
        combine(userRepository.nickname, step) { nickname, step ->
            GlobeUiState(nickname = nickname?.takeIf { it.isNotBlank() } ?: DEFAULT_NICKNAME, step = step)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobeUiState())

    init {
        restartAutoCycle()
    }

    fun onStepSelected(selected: GlobeStep) {
        step.value = selected
        restartAutoCycle()
    }

    // 3.4초마다 다음 단계로 자동 순환. 직접 고르면 타이머를 처음부터 다시 잰다.
    private fun restartAutoCycle() {
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_STEP_MS)
                val steps = GlobeStep.entries
                step.value = steps[(step.value.ordinal + 1) % steps.size]
            }
        }
    }

    private companion object {
        const val AUTO_STEP_MS = 3_400L
    }
}
