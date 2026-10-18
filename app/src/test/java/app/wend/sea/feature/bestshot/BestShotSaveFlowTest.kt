package app.wend.sea.feature.bestshot

import app.wend.sea.data.BestShotSaveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BestShotSaveFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val mode = MutableStateFlow(BestShotSaveMode.UNSET)
    private var commits = 0
    private var queried = 0
    private var query: suspend (List<Long>) -> List<Long> = { it }
    private var persist: suspend (BestShotSaveMode) -> Unit = { mode.value = it }
    private val results = mutableListOf<BestShotSaveResult>()
    private val flow = BestShotSaveFlow(scope, mode, { persist(it) }, { queried++; query(it) })

    @After fun stop() { scope.cancel() }
    private fun submit(ids: List<Long> = listOf(1L)) = flow.submit(ids, { commits++ }, { results += it })

    @Test fun firstSelectionWaitsBeforeSaving() {
        submit()
        assertTrue(flow.state.value.choosingMode)
        assertEquals(0, commits)
        assertTrue(results.isEmpty())
        flow.selectMode(BestShotSaveMode.APP_ONLY)
        assertEquals(BestShotSaveMode.APP_ONLY, mode.value)
        assertEquals(1, commits)
        assertEquals(0, queried)
        assertEquals(listOf(BestShotSaveResult.SAVED), results)
    }

    @Test fun dismissDoesNotSaveOrSetPreference() {
        submit(); flow.dismissMode()
        assertFalse(flow.state.value.busy)
        assertEquals(0, commits)
        assertEquals(BestShotSaveMode.UNSET, mode.value)
        assertTrue(results.isEmpty())
        submit()
        assertTrue(flow.state.value.choosingMode)
    }

    @Test fun appOnlyDoesNotAskAgainAcrossSaveFlows() {
        mode.value = BestShotSaveMode.APP_ONLY
        submit(); submit(listOf(2L))
        val next = BestShotSaveFlow(scope, mode, { mode.value = it }, { error("must not query") })
        next.submit(listOf(3L), { commits++ }, { results += it })
        assertEquals(3, commits)
        assertFalse(next.state.value.choosingMode)
        assertEquals(0, queried)
    }

    @Test fun gallerySavesBeforeApprovalButCompletesAfterApproval() {
        submit(); flow.selectMode(BestShotSaveMode.GALLERY)
        assertEquals(1, commits)
        assertTrue(results.isEmpty())
        assertEquals(listOf(1L), flow.state.value.approval!!.ids)
        flow.approvalResult(true)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_ADDED), results)
    }

    @Test fun denialKeepsAppSaveAndPreference() {
        submit(); flow.selectMode(BestShotSaveMode.GALLERY)
        flow.approvalResult(false)
        assertEquals(1, commits)
        assertEquals(BestShotSaveMode.GALLERY, mode.value)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_DECLINED), results)
    }

    @Test fun launchFailureKeepsAppSaveAndPreference() {
        mode.value = BestShotSaveMode.GALLERY
        submit(); flow.approvalResult(false, failed = true)
        assertEquals(1, commits)
        assertEquals(BestShotSaveMode.GALLERY, mode.value)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_FAILED), results)
    }

    @Test fun queryFailureDoesNotUndoAppSave() {
        mode.value = BestShotSaveMode.GALLERY
        query = { throw SecurityException("permission revoked") }
        submit()
        assertEquals(1, commits)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_FAILED), results)
    }

    @Test fun existingFavoritesDoNotRequestApproval() {
        mode.value = BestShotSaveMode.GALLERY
        query = { emptyList() }
        submit()
        assertNull(flow.state.value.approval)
        assertEquals(listOf(BestShotSaveResult.SAVED), results)
    }

    @Test fun onlyUnfavoritedDistinctPhotosAreRequested() {
        mode.value = BestShotSaveMode.GALLERY
        query = { ids -> ids.filter { it != 1L } }
        submit(listOf(1L, 2L, 2L))
        assertEquals(listOf(2L), flow.state.value.approval!!.ids)
    }

    @Test fun removingSelectionsDoesNotAskOrTouchGallery() {
        submit(emptyList())
        assertEquals(1, commits)
        assertEquals(BestShotSaveMode.UNSET, mode.value)
        assertEquals(0, queried)
        mode.value = BestShotSaveMode.GALLERY
        submit(emptyList())
        assertEquals(0, queried)
    }

    @Test fun duplicateSubmitAndModeTapsDoNotDoubleSave() {
        submit(); submit(listOf(2L))
        flow.selectMode(BestShotSaveMode.GALLERY)
        flow.selectMode(BestShotSaveMode.APP_ONLY)
        submit(listOf(3L))
        assertEquals(1, commits)
        assertEquals(listOf(1L), flow.state.value.approval!!.ids)
        assertEquals(BestShotSaveMode.GALLERY, mode.value)
    }

    @Test fun batchesWaitForEachApprovalAndStopOnDenial() {
        mode.value = BestShotSaveMode.GALLERY
        submit((1L..1200L).toList())
        val first = flow.state.value.approval!!
        assertEquals(500, first.ids.size)
        flow.approvalLaunched(first.token)
        assertTrue(flow.state.value.approval!!.launched)
        flow.approvalResult(true)
        val second = flow.state.value.approval!!
        assertEquals(501L, second.ids.first())
        assertFalse(second.launched)
        assertTrue(results.isEmpty())
        flow.approvalResult(false)
        assertNull(flow.state.value.approval)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_DECLINED), results)
        assertEquals(1, commits)
    }

    @Test fun allBatchesCompleteOnce() {
        mode.value = BestShotSaveMode.GALLERY
        submit((1L..1001L).toList())
        repeat(3) { flow.approvalResult(true) }
        flow.approvalResult(true)
        assertEquals(listOf(BestShotSaveResult.FAVORITES_ADDED), results)
    }

    @Test fun preferenceFailureDoesNotSavePhotos() {
        persist = { throw java.io.IOException("disk full") }
        submit(); flow.selectMode(BestShotSaveMode.GALLERY)
        assertEquals(0, commits)
        assertEquals(listOf(BestShotSaveResult.SAVE_FAILED), results)
    }

    @Test fun appSaveFailureNeverTouchesGallery() {
        mode.value = BestShotSaveMode.GALLERY
        flow.submit(listOf(1L), { error("database failure") }, { results += it })
        assertEquals(0, queried)
        assertEquals(listOf(BestShotSaveResult.SAVE_FAILED), results)
    }

    @Test fun storedModesAreValidated() {
        assertEquals(BestShotSaveMode.UNSET, BestShotSaveMode.fromStored(null))
        assertEquals(BestShotSaveMode.UNSET, BestShotSaveMode.fromStored("unknown"))
        BestShotSaveMode.entries.forEach { assertEquals(it, BestShotSaveMode.fromStored(it.name)) }
    }
}
