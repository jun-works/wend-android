package app.wend.sea.feature.globe

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.R
import app.wend.sea.core.text.AppText
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
enum class GlobeStep(val number: String, @StringRes val label: Int) {
    When("01", R.string.globe_step_when),
    Where("02", R.string.globe_step_where),
    Group("03", R.string.globe_step_group),
}

data class GlobeUiState(
    val nickname: String = defaultNickname,
    val step: GlobeStep = GlobeStep.When,
)

/** 닉네임을 아직 안 정했을 때 부르는 이름 */
val defaultNickname: String get() = AppText.string(R.string.default_nickname)

@HiltViewModel
class GlobeViewModel @Inject constructor(
    userRepository: UserRepository,
) : ViewModel() {

    private val step = MutableStateFlow(GlobeStep.When)
    private var autoJob: Job? = null

    val uiState: StateFlow<GlobeUiState> =
        combine(userRepository.nickname, step) { nickname, step ->
            GlobeUiState(nickname = nickname?.takeIf { it.isNotBlank() } ?: defaultNickname, step = step)
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
