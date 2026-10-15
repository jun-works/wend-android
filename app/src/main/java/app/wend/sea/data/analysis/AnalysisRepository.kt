package app.wend.sea.data.analysis

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.MediaThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val mediaRepository: MediaRepository,
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

    /** 여행 하나와, 그 사진들을 장소 단위로 나눈 것 */
    suspend fun tripDetail(number: Int): Pair<Trip, List<Place>>? {
        val entity = tripDao.trip(number) ?: return null
        val keys = tripDao.media(number).map { it.video to it.mediaId }
        val points = mediaRepository.points(keys)
        val places = withContext(Dispatchers.Default) { PlaceGrouper().group(points) }
        return Trip(entity.number, entity.start, entity.end, points.size) to places
    }

    /** 여행마다 카드에 보일 사진 3장: 첫 장 · 가운데 장 · 마지막 장 */
    suspend fun covers(): Map<Int, List<MediaThumbnail>> =
        tripDao.allMedia().groupBy { it.tripNumber }.mapValues { (_, media) ->
            listOf(0, media.size / 2, media.lastIndex).distinct().filter { it in media.indices }
                .map { MediaThumbnail(media[it].mediaId, media[it].video) }
        }

    private companion object {
        const val WORK_NAME = "trip-analysis"
    }
}
