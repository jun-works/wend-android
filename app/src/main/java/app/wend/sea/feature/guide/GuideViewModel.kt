package app.wend.sea.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.core.text.AppText
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.globe.defaultNickname
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 말풍선 텍스트의 `**굵게**` 표시는 화면에서 굵은 글씨로 바뀐다. */
sealed interface ChatItem {
    val id: Int

    data class Day(override val id: Int) : ChatItem

    data class BotMessage(
        override val id: Int,
        val bot: Bot,
        val text: String,
        val showHeader: Boolean,
        val isKey: Boolean = false,
        val withExampleCard: Boolean = false,
    ) : ChatItem

    data class Typing(override val id: Int, val bot: Bot, val showHeader: Boolean) : ChatItem

    data class UserMessage(override val id: Int, val text: String) : ChatItem
}

data class QuickReply(val question: String, val bot: Bot, val answer: String)

data class GuideUiState(
    val items: List<ChatItem> = emptyList(),
    val replies: List<QuickReply> = emptyList(),
    val canFinish: Boolean = false,
)

sealed interface GuideEvent {
    /** 안내를 다 듣고 다음(사진 접근)으로 */
    data object Finished : GuideEvent
}

private data class Line(
    val bot: Bot,
    val text: String,
    val isKey: Boolean = false,
    val withExampleCard: Boolean = false,
)

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    private val _events = Channel<GuideEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val allReplies = listOf(
        QuickReply(AppText.string(R.string.guide_q_deleted), Bot.Tick, AppText.string(R.string.guide_a_deleted)),
        QuickReply(AppText.string(R.string.guide_q_location), Bot.Pin, AppText.string(R.string.guide_a_location)),
        QuickReply(AppText.string(R.string.guide_q_video), Bot.Tick, AppText.string(R.string.guide_a_video)),
    )
    private val usedReplies = mutableSetOf<QuickReply>()

    private var nickname = defaultNickname
    private var playJob: Job? = null
    private var nextId = 0
    private var lastBot: Bot? = null

    init {
        viewModelScope.launch {
            nickname = userRepository.nickname.firstOrNull()?.takeIf { it.isNotBlank() } ?: defaultNickname
            play()
        }
    }

    fun onReplay() = play()

    fun onReplyClick(reply: QuickReply) {
        if (playJob?.isActive == true) return
        usedReplies += reply
        lastBot = null
        _uiState.update {
            it.copy(items = it.items + ChatItem.UserMessage(nextId++, reply.question), replies = emptyList())
        }
        playJob = viewModelScope.launch {
            say(Line(reply.bot, reply.answer))
            showRemainingReplies()
        }
    }

    fun onFinish() {
        viewModelScope.launch {
            userRepository.setOnboardingDone()
            _events.send(GuideEvent.Finished)
        }
    }

    private fun play() {
        playJob?.cancel()
        usedReplies.clear()
        lastBot = null
        _uiState.value = GuideUiState(items = listOf(ChatItem.Day(nextId++)))
        playJob = viewModelScope.launch {
            script().forEach { say(it) }
            _uiState.update { it.copy(canFinish = true) }
            showRemainingReplies()
        }
    }

    private fun script() = listOf(
        Line(Bot.Tick, AppText.string(R.string.guide_line_1, nickname)),
        Line(Bot.Pin, AppText.string(R.string.guide_line_2)),
        Line(Bot.Tick, AppText.string(R.string.guide_line_3)),
        Line(Bot.Pin, AppText.string(R.string.guide_line_4)),
        Line(Bot.Tick, AppText.string(R.string.guide_line_5), isKey = true),
        Line(Bot.Pin, AppText.string(R.string.guide_line_6), withExampleCard = true),
    )

    // 입력 중 표시 → 글자 수에 비례해 기다렸다가 → 말풍선
    private suspend fun say(line: Line) {
        val showHeader = lastBot != line.bot
        val typing = ChatItem.Typing(nextId++, line.bot, showHeader)
        _uiState.update { it.copy(items = it.items + typing) }
        delay(650L + minOf(1300, line.text.replace("**", "").length * 22))
        val message = ChatItem.BotMessage(nextId++, line.bot, line.text, showHeader, line.isKey, line.withExampleCard)
        lastBot = line.bot
        _uiState.update { it.copy(items = it.items - typing + message) }
        delay(if (line.withExampleCard) 900L else 420L)
    }

    private fun showRemainingReplies() {
        _uiState.update { it.copy(replies = allReplies.filterNot { reply -> reply in usedReplies }) }
    }
}
