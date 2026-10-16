package app.wend.sea.feature.left

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.core.designsystem.component.tripColor
import app.wend.sea.data.LibraryItem
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.TripChoice
import app.wend.sea.feature.analysis.name
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** 찍은 날짜 하나: "6월 20일 토요일" · 2026 · 장수. [date] 가 null 이면 촬영 시각이 없는 사진·영상 묶음(맨 끝) */
data class LeftDayUi(val date: LocalDate?, val label: String, val items: List<LibraryItem>) {
    val key: String get() = date?.toString() ?: "undated"
}

/** 새 여행 대화상자: "2026. 6. 20 · 7장" 과 미리 채울 이름 */
data class NewTripUi(val context: String, val suggested: String)

/** 고른 사진이 여행 색 방울로 뭉쳐 들어가는 중 — 끝나면 [LeftViewModel.onFlightDone] */
data class FlightUi(val media: List<MediaThumbnail>, val color: Color)

data class LeftUiState(
    val loaded: Boolean = false,
    val days: List<LeftDayUi> = emptyList(),
    val total: Int = 0,
    val selecting: Boolean = false,
    val selected: Set<MediaThumbnail> = emptySet(),
    /** 여행에 넣기 시트(가까운 여행부터), 닫혀 있으면 null */
    val choices: List<TripChoice>? = null,
    val newTrip: NewTripUi? = null,
    val flight: FlightUi? = null,
) {
    /** 크게 보기에 넘길 순서(화면과 같은 최신순) */
    val items: List<LibraryItem> get() = days.flatMap { it.items }
}

/**
 * 시안 ⑰ 빠진 사진: 여행에 담기지 않은 사진을 찍은 날짜순(최신순)으로. 왜 빠졌는지는 보여 주지 않는다.
 * 골라서 기존 여행에 넣거나(시간이 가까운 여행부터) 새 여행을 만든다. 처음부터 다시 찾으면 정책대로 새로 묶인다.
 */
@HiltViewModel
class LeftViewModel @Inject constructor(
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeftUiState())
    val uiState: StateFlow<LeftUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 방울이 다 뭉치면 할 일 */
    private var pending: (suspend () -> String)? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val items = analysisRepository.leftMedia()
            val zone = ZoneId.systemDefault()
            val (dated, undated) = items.partition { it.dated }
            val days = dated.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
                .map { (date, list) -> LeftDayUi(date, dayLabel(date), list) } +
                listOfNotNull(undated.takeIf { it.isNotEmpty() }?.let { LeftDayUi(null, "날짜 정보 없음", it) })
            _uiState.update { it.copy(loaded = true, days = days, total = items.size) }
        }
    }

    /** 크게 보기에서 바꿨다(여행에 넣음 · 새 여행 · 휴지통). 빈 문자열이면 안내 없이 다시 읽기만 */
    fun onResult(message: String) {
        load()
        if (message.isNotEmpty()) _messages.trySend(message)
    }

    fun setSelecting(on: Boolean) = _uiState.update { it.copy(selecting = on, selected = if (on) it.selected else emptySet()) }

    fun toggle(media: MediaThumbnail) = _uiState.update { s ->
        s.copy(selecting = true, selected = if (media in s.selected) s.selected - media else s.selected + media)
    }

    /** 날짜의 "모두 선택" — 이미 다 골랐으면 그날만 해제 */
    fun toggleDay(key: String) = _uiState.update { s ->
        val day = s.days.firstOrNull { it.key == key }?.items?.map { it.media } ?: return@update s
        s.copy(selecting = true, selected = if (s.selected.containsAll(day)) s.selected - day.toSet() else s.selected + day)
    }

    fun askAdd() {
        viewModelScope.launch {
            val choices = analysisRepository.tripChoices(selectedTimes())
            _uiState.update { it.copy(choices = choices) }
        }
    }

    fun askNew() {
        viewModelScope.launch {
            val picked = _uiState.value.selected.toList()
            val times = selectedTimes()
            if (times.isEmpty()) {
                _messages.send("찍은 날짜가 있는 사진이 있어야 여행을 만들 수 있어요")
                return@launch
            }
            val suggested = analysisRepository.suggestTripName(picked) ?: dateTripName(times)
            _uiState.update { it.copy(newTrip = NewTripUi("${rangeText(times)} · ${picked.size}장", suggested)) }
        }
    }

    fun dismissPickers() = _uiState.update { it.copy(choices = null, newTrip = null) }

    fun addTo(choice: TripChoice) {
        val picked = _uiState.value.selected.toList()
        pending = {
            analysisRepository.addToTrip(choice.trip.number, picked)
            "${picked.size}장을 ${tripPhrase(choice.trip.name)}에 넣었어요"
        }
        _uiState.update { it.copy(choices = null, flight = FlightUi(picked, tripColor(choice.trip.number))) }
    }

    fun makeTrip(name: String) {
        val picked = _uiState.value.selected.toList()
        viewModelScope.launch {
            // 새 여행이 들어갈 자리의 색 — 시작 시각 순서로 번호가 매겨진다
            val start = selectedTimes().min()
            val number = analysisRepository.trips().count { it.start < start } + 1
            pending = {
                if (analysisRepository.newTrip(picked, name) != null) {
                    "‘$name’ 여행을 만들었어요. 여행 목록에서 볼 수 있어요"
                } else {
                    "찍은 날짜가 있는 사진이 있어야 여행을 만들 수 있어요"
                }
            }
            _uiState.update { it.copy(newTrip = null, flight = FlightUi(picked, tripColor(number))) }
        }
    }

    /** 방울이 다 뭉쳤다(움직임 줄이기면 바로) — 실제로 옮기고 목록을 다시 읽는다 */
    fun onFlightDone() {
        val action = pending ?: return
        pending = null
        viewModelScope.launch {
            val message = action()
            _uiState.update { it.copy(flight = null, selecting = false, selected = emptySet()) }
            load()
            _messages.send(message)
        }
    }

    /** 고른 사진의 촬영 시각(촬영 시각이 없는 것은 빼고) */
    private fun selectedTimes(): List<Long> {
        val s = _uiState.value
        return s.items.filter { it.media in s.selected && it.dated }.map { it.time }
    }

    private fun dayLabel(date: LocalDate): String =
        "${date.monthValue}월 ${date.dayOfMonth}일 ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}"
}
