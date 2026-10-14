package app.wend.sea.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.core.designsystem.component.Bot
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.globe.DEFAULT_NICKNAME
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
        QuickReply("빠진 사진은 지워지나요?", Bot.Tick, "아니요! 직접 고르기 전에는 사진을 지우거나 옮기지 않아요. 여행 정리에서만 빠지는 거예요."),
        QuickReply("위치는 어떻게 남겨요?", Bot.Pin, "카메라 앱 설정에서 **위치 태그**를 켜 두면 여행을 더 정확하게 찾아요."),
        QuickReply("영상도 똑같아요?", Bot.Tick, "네, 영상도 사진과 똑같아요."),
    )
    private val usedReplies = mutableSetOf<QuickReply>()

    private var nickname = DEFAULT_NICKNAME
    private var playJob: Job? = null
    private var nextId = 0
    private var lastBot: Bot? = null

    init {
        viewModelScope.launch {
            nickname = userRepository.nickname.firstOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_NICKNAME
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
        Line(Bot.Tick, "${nickname}님, 반가워요! 저는 사진이 **언제** 찍혔는지 읽는 틱이에요."),
        Line(Bot.Pin, "저는 **어디서** 찍혔는지 읽는 핀이고요. 둘이 힘을 합쳐서 사진을 여행별로 묶어 드려요."),
        Line(Bot.Tick, "시작하기 전에 꼭 알려 드릴 게 하나 있어요."),
        Line(Bot.Pin, "사진과 영상 안에는 보통 **촬영 시간**과 **위치**가 같이 저장돼요. 저희는 그걸 보고 여행을 찾아요."),
        Line(Bot.Tick, "그래서 시간이나 위치 정보가 없는 사진·영상은 **여행에 담기지 않을 수 있어요.**", isKey = true),
        Line(Bot.Pin, "예를 들면 이렇게요.", withExampleCard = true),
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
