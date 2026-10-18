package app.wend.sea.feature.bestshot

import app.wend.sea.data.BestShotSaveMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 앱 저장 성공과 갤러리 승인 결과는 분리한다. 거부/실패해도 앱 선택과 저장 방식은 유지한다. */
enum class BestShotSaveResult { SAVED, FAVORITES_ADDED, FAVORITES_DECLINED, FAVORITES_FAILED, SAVE_FAILED }
data class FavoriteApproval(val token: Int, val ids: List<Long>, val launched: Boolean = false)
data class BestShotSaveState(
    val busy: Boolean = false,
    val choosingMode: Boolean = false,
    val approval: FavoriteApproval? = null,
)

/** 두 확정 경로가 공유하는 흐름. ViewModel과 수명이 같아 회전 시 안내/승인을 중복 요청하지 않는다. */
class BestShotSaveFlow(
    private val scope: CoroutineScope,
    private val mode: Flow<BestShotSaveMode>,
    private val setMode: suspend (BestShotSaveMode) -> Unit,
    private val notFavoriteIds: suspend (List<Long>) -> List<Long>,
) {
    private val _state = MutableStateFlow(BestShotSaveState())
    val state = _state.asStateFlow()
    private var photos = emptyList<Long>()
    private var commit: (suspend () -> Unit)? = null
    private var completed: (suspend (BestShotSaveResult) -> Unit)? = null
    private var remaining = emptyList<List<Long>>()
    private var token = 0

    fun submit(ids: List<Long>, save: suspend () -> Unit, onComplete: suspend (BestShotSaveResult) -> Unit) {
        if (_state.value.busy) return
        photos = ids.distinct()
        commit = save
        completed = onComplete
        _state.value = BestShotSaveState(busy = true)
        scope.launch {
            try {
                val selected = mode.first()
                if (photos.isNotEmpty() && selected == BestShotSaveMode.UNSET) {
                    _state.value = BestShotSaveState(busy = true, choosingMode = true)
                } else this@BestShotSaveFlow.save(selected)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { finish(BestShotSaveResult.SAVE_FAILED) }
        }
    }

    fun selectMode(selected: BestShotSaveMode) {
        if (!_state.value.choosingMode || selected == BestShotSaveMode.UNSET) return
        _state.value = BestShotSaveState(busy = true)
        scope.launch {
            try {
                setMode(selected)
                save(selected)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { finish(BestShotSaveResult.SAVE_FAILED) }
        }
    }

    fun dismissMode() {
        if (!_state.value.choosingMode) return
        clear()
    }

    private suspend fun save(selected: BestShotSaveMode) {
        commit?.invoke()
        commit = null
        if (selected != BestShotSaveMode.GALLERY || photos.isEmpty()) {
            finish(BestShotSaveResult.SAVED)
            return
        }
        try {
            // 2,000개 제한보다 작게 나누고, 이미 즐겨찾기인 사진은 요청하지 않는다.
            remaining = notFavoriteIds(photos).distinct().chunked(500)
            if (remaining.isEmpty()) finish(BestShotSaveResult.SAVED) else nextApproval()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { finish(BestShotSaveResult.FAVORITES_FAILED) }
    }

    private fun nextApproval() {
        val batch = remaining.first()
        remaining = remaining.drop(1)
        _state.value = BestShotSaveState(busy = true, approval = FavoriteApproval(++token, batch))
    }

    fun approvalLaunched(token: Int) {
        val approval = _state.value.approval ?: return
        if (approval.token == token) _state.value = _state.value.copy(approval = approval.copy(launched = true))
    }

    fun approvalResult(allowed: Boolean, failed: Boolean = false) {
        if (_state.value.approval == null) return
        _state.value = BestShotSaveState(busy = true)
        scope.launch {
            when {
                failed -> finish(BestShotSaveResult.FAVORITES_FAILED)
                !allowed -> finish(BestShotSaveResult.FAVORITES_DECLINED)
                remaining.isNotEmpty() -> nextApproval()
                else -> finish(BestShotSaveResult.FAVORITES_ADDED)
            }
        }
    }

    private suspend fun finish(result: BestShotSaveResult) {
        val callback = completed
        clear()
        callback?.invoke(result)
    }

    private fun clear() {
        photos = emptyList(); remaining = emptyList(); commit = null; completed = null
        _state.value = BestShotSaveState()
    }
}
