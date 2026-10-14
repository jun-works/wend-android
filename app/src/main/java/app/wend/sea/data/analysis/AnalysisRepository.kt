package app.wend.sea.data.analysis

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.UserRepository
import app.wend.sea.data.local.TripDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 찾은 여행 하나. [number] 는 찍은 순서(오래된 것이 1) — 지명 정책이 정해지기 전까지 "여행 N" 으로 부른다 */
data class Trip(val number: Int, val start: Long, val end: Long, val mediaCount: Int)

sealed interface AnalysisState {
    /** 분석을 시작한 적 없음 */
    data object Idle : AnalysisState

    data class Running(val phase: AnalysisPhase, val done: Int, val total: Int) : AnalysisState

    data object Finished : AnalysisState

    data object Failed : AnalysisState
}

@Singleton
class AnalysisRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val tripDao: TripDao,
    private val userRepository: UserRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    /** 이미 돌고 있으면 그대로 둔다 */
    fun start() {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnalysisWorker>().build(),
        )
    }

    val state: Flow<AnalysisState> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        val info = infos.lastOrNull() ?: return@map AnalysisState.Idle
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> AnalysisState.Running(AnalysisPhase.Reading, 0, 0)
            WorkInfo.State.RUNNING -> AnalysisState.Running(
                phase = AnalysisPhase.entries[info.progress.getInt(AnalysisWorker.KEY_PHASE, 0)],
                done = info.progress.getInt(AnalysisWorker.KEY_DONE, 0),
                total = info.progress.getInt(AnalysisWorker.KEY_TOTAL, 0),
            )
            WorkInfo.State.SUCCEEDED -> AnalysisState.Finished
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> AnalysisState.Failed
        }
    }

    val summary: Flow<AnalysisSummary?> = userRepository.analysisSummary

    suspend fun trips(): List<Trip> = tripDao.trips().map { Trip(it.number, it.start, it.end, it.mediaCount) }

    private companion object {
        const val WORK_NAME = "trip-analysis"
    }
}
