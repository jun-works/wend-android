package app.wend.sea.feature.nickname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.R
import app.wend.sea.core.text.AppText
import app.wend.sea.data.UserRepository
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

enum class NicknameHelp { Default, InvalidChars, TooShort }

data class NicknameUiState(
    val input: String = "",
    val showSubmitError: Boolean = false,
) {
    private val trimmed get() = input.trim()

    /** 2~10자, 한글·영문·숫자(띄어쓰기 허용) */
    val isValid: Boolean get() = VALID.matches(trimmed)

    val help: NicknameHelp
        get() = when {
            trimmed.isNotEmpty() && !ALLOWED.matches(trimmed) -> NicknameHelp.InvalidChars
            showSubmitError && !isValid -> NicknameHelp.TooShort
            else -> NicknameHelp.Default
        }

    val isError: Boolean get() = help != NicknameHelp.Default

    companion object {
        const val MAX_LENGTH = 10
        // 어느 언어든 글자·숫자·공백(악센트 같은 결합 문자 포함)
        private val VALID = Regex("""^[\p{L}\p{M}\p{N} ]{2,10}$""")
        private val ALLOWED = Regex("""^[\p{L}\p{M}\p{N} ]*$""")
    }
}

sealed interface NicknameEvent {
    data object Saved : NicknameEvent
    data object FocusInput : NicknameEvent

    /** 제출했지만 이름이 유효하지 않음 */
    data object Rejected : NicknameEvent
}

@HiltViewModel
class NicknameViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NicknameUiState())
    val uiState: StateFlow<NicknameUiState> = _uiState.asStateFlow()

    private val _events = Channel<NicknameEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val suggestions = listOf(
        R.string.nickname_suggestion_1,
        R.string.nickname_suggestion_2,
        R.string.nickname_suggestion_3,
        R.string.nickname_suggestion_4,
    ).map { AppText.string(it) }

    init {
        viewModelScope.launch {
            val saved = userRepository.nickname.firstOrNull()
            if (!saved.isNullOrEmpty() && _uiState.value.input.isEmpty()) {
                _uiState.update { it.copy(input = saved) }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value.take(NicknameUiState.MAX_LENGTH), showSubmitError = false) }
    }

    fun onSuggestionClick(suggestion: String) {
        onInputChange(suggestion)
        _events.trySend(NicknameEvent.FocusInput)
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update { it.copy(showSubmitError = true) }
            _events.trySend(NicknameEvent.Rejected)
            return
        }
        viewModelScope.launch {
            userRepository.saveNickname(state.input.trim())
            _events.send(NicknameEvent.Saved)
        }
    }
}
