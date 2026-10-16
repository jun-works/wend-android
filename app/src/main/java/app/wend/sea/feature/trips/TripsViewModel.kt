package app.wend.sea.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wend.sea.R
import app.wend.sea.core.text.AppText
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import app.wend.sea.data.analysis.AnalysisRepository
import app.wend.sea.data.analysis.Trip
import app.wend.sea.feature.analysis.autoName
import app.wend.sea.feature.analysis.name
import app.wend.sea.feature.permission.PermissionResultHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class TripsUiState(
    val loaded: Boolean = false,
    /** 찍은 순서(오래된 것부터) — 지구본 덩어리 순서와 같다 */
    val trips: List<Trip> = emptyList(),
    val covers: Map<Int, List<MediaThumbnail>> = emptyMap(),
    val summary: AnalysisSummary? = null,
    /** 방금 이름을 바꾼 여행 — 카드 이름이 한 번 빛난다. [renamedToken] 이 바뀔 때마다 다시 */
    val renamed: Int? = null,
    val renamedToken: Long = 0,
    /** 여행에 담기지 않은 사진·영상 수 — 빠진 사진 화면과 같이 지금 기기 사진으로 센다 */
    val leftCount: Int = 0,
    /** 사진을 모두 허용했다 — 처음부터 다시 찾을지 묻는 중 */
    val confirmReanalyze: Boolean = false,
) {
    val partial: Boolean get() = summary?.partial == true
    val includedCount: Int get() = trips.sumOf { it.mediaCount }

    /** 최신순 연도별 묶음 */
    val years: List<Pair<Int, List<Trip>>>
        get() = trips.reversed().groupBy { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).year }.toList()
}

sealed interface TripsEvent {
    data class ShowMessage(val message: String) : TripsEvent

    /** 이름을 바꿨다 — 스낵바의 되돌리기로 [undo] */
    data class Renamed(val message: String, val undo: () -> Unit) : TripsEvent

    /** 모든 사진을 허용해서 처음부터 다시 찾는다 */
    data object Reanalyze : TripsEvent
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    private val resultHandler: PermissionResultHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var restarting = false

    /** 다시 찾기를 한 번 미뤘다 — 화면에 돌아올 때마다 다시 묻지 않는다(일부 허용 안내를 직접 누르면 다시 묻는다) */
    private var declined = false

    init {
        viewModelScope.launch {
            val trips = analysisRepository.trips()
            _uiState.update {
                it.copy(
                    loaded = true,
                    trips = trips,
                    covers = analysisRepository.covers(),
                    summary = analysisRepository.summary.firstOrNull(),
                    leftCount = analysisRepository.leftMedia().size,
                )
            }
            // 최근 여행(목록 위쪽)의 상세를 미리 읽어 두어, 열 때 방울이 걷히자마자 내용이 보이게
            analysisRepository.prefetchTripDetails(trips.takeLast(3).reversed().map { it.number })
        }
    }

    /** 권한 안내창 결과: 모두 허용이 되면 처음부터 다시 찾는다 */
    fun onRequestResult(access: MediaAccess, showRationale: Boolean) {
        viewModelScope.launch {
            resultHandler.record(access, showRationale)
            restartIfAllowedAll(access, fromUser = true)
        }
    }

    /** 설정에서 돌아옴 · 화면 복귀 */
    /** 상세·빠진 사진에서 돌아왔을 때 — 여행 목록을 다시 읽는다 */
    fun refreshTrips() {
        if (!_uiState.value.loaded) return
        viewModelScope.launch {
            val trips = analysisRepository.trips()
            // 빠진 사진에서 넣거나 새 여행을 만들고 오면 대표 사진·요약도 바뀐다
            val covers = analysisRepository.covers()
            val summary = analysisRepository.summary.firstOrNull()
            val left = analysisRepository.leftMedia().size
            _uiState.update { it.copy(trips = trips, covers = covers, summary = summary, leftCount = left) }
        }
    }

    /** 여행 이름 바꾸기. 처음 이름과 같게 적으면 자동 이름으로 돌아간다 */
    fun renameTrip(number: Int, name: String) {
        val trip = _uiState.value.trips.firstOrNull { it.number == number } ?: return
        if (name == trip.name) return
        val before = trip.userName
        setTripName(number, name.takeIf { it != trip.autoName })
        _events.trySend(TripsEvent.Renamed(AppText.string(R.string.trip_renamed)) { setTripName(number, before) })
    }

    private fun setTripName(number: Int, userName: String?) {
        _uiState.update { s ->
            s.copy(
                trips = s.trips.map { if (it.number == number) it.copy(userName = userName) else it },
                renamed = number,
                renamedToken = s.renamedToken + 1,
            )
        }
        viewModelScope.launch { analysisRepository.renameTrip(number, userName) }
    }

    /** 화면 복귀([fromUser] = false) · 설정에서 돌아옴(true) */
    fun checkAccess(fromUser: Boolean = false) {
        viewModelScope.launch { restartIfAllowedAll(mediaRepository.currentAccess(), fromUser) }
    }

    /** 모두 허용이 됐으면 처음부터 다시 찾을지 묻는다 — 다시 찾기는 초기화라 묻지 않고 돌리지 않는다 */
    private fun restartIfAllowedAll(access: MediaAccess, fromUser: Boolean) {
        if (restarting || access != MediaAccess.All || !_uiState.value.partial) return
        if (declined && !fromUser) return
        _uiState.update { it.copy(confirmReanalyze = true) }
    }

    fun onReanalyzeConfirmed() {
        _uiState.update { it.copy(confirmReanalyze = false) }
        if (restarting) return
        restarting = true
        analysisRepository.start()
        _events.trySend(TripsEvent.Reanalyze)
    }

    fun onReanalyzeDismissed() {
        declined = true
        _uiState.update { it.copy(confirmReanalyze = false) }
    }
}
