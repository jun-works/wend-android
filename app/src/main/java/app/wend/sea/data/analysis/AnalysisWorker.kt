package app.wend.sea.data.analysis

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.UserRepository
import app.wend.sea.data.local.TripDao
import app.wend.sea.data.local.TripEntity
import app.wend.sea.data.local.TripMediaEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 여행 찾기. 포그라운드로 돌아서 앱을 닫아도 계속되고, 끝나면 완료 알림을 보낸다.
 * 진행은 [AnalysisPhase] 와 읽은 수로 화면에 알린다.
 */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val userRepository: UserRepository,
    private val tripDao: TripDao,
    private val notifier: AnalysisNotifier,
    private val cityPicker: TripCityPicker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val access = mediaRepository.currentAccess()
        if (access == MediaAccess.None) return Result.failure()

        notifier.cancelDone()
        // 앱이 앞에 있을 때 시작하므로 포그라운드 전환이 허용된다. 실패해도 분석은 계속한다.
        runCatching { setForeground(notifier.progressInfo(0, 0)) }
        report(AnalysisPhase.Reading, 0, 0)

        var lastNotified = 0L
        val points = mediaRepository.analysisPoints { progress ->
            report(AnalysisPhase.Places, progress.done, progress.total)
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotified > 1_000 || progress.done == progress.total) {
                lastNotified = now
                runCatching { setForeground(notifier.progressInfo(progress.done, progress.total)) }
            }
        }

        report(AnalysisPhase.Grouping, points.size, points.size)
        // 사용자가 여행에서 뺀 사진은 다시 넣지 않는다
        val excluded = tripDao.excluded().map { it.video to it.mediaId }.toSet()
        val trips = withContext(Dispatchers.Default) {
            TripDetector().detect(points)
                .map { t -> t.copy(media = t.media.filter { (it.video to it.id) !in excluded }) }
                .filter { t -> t.media.any { it.takenAt != null } }
                .map { t -> t.copy(start = t.media.mapNotNull { it.takenAt }.min(), end = t.media.mapNotNull { it.takenAt }.max()) }
        }
        val cities = trips.map { cityPicker.pick(it.media) }

        tripDao.replaceAll(
            trips = trips.mapIndexed { i, t ->
                TripEntity(
                    number = i + 1, start = t.start, end = t.end, mediaCount = t.media.size,
                    cityId = cities[i].cityId, cityShare = cities[i].share, countryCode = cities[i].countryCode,
                )
            },
            media = trips.flatMapIndexed { i, t -> t.media.map { TripMediaEntity(i + 1, it.id, it.video) } },
        )
        userRepository.saveAnalysisSummary(
            AnalysisSummary(
                total = mediaRepository.visibleCount(),
                included = trips.sumOf { it.media.size },
                tripCount = trips.size,
                partial = access == MediaAccess.Partial,
            ),
        )
        notifier.notifyDone(trips.size)
        return Result.success()
    }

    private suspend fun report(phase: AnalysisPhase, done: Int, total: Int) {
        setProgress(workDataOf(KEY_PHASE to phase.ordinal, KEY_DONE to done, KEY_TOTAL to total))
    }

    companion object {
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
    }
}

/** 틱이 읽음(시간) → 핀이 장소를 봄(위치) → 같은 여행끼리 모음 */
enum class AnalysisPhase { Reading, Places, Grouping }
