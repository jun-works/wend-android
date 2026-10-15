package app.wend.sea.data.analysis

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.wend.sea.data.MediaRepository
import app.wend.sea.data.ThermalGuard
import app.wend.sea.data.local.MediaFeatureDao
import app.wend.sea.data.local.MediaFeatureEntity
import app.wend.sea.data.local.TripDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 여행 사진의 dHash·밝기 값을 계산해 저장한다 — design/BEST_SHOT.md.
 * 여행 찾기가 끝나면 뒤에서 돌고(여행 찾기 화면을 기다리게 하지 않음), 파일이 바뀌지 않은 사진은 건너뛴다.
 * 멈췄다 다시 시작해도 저장해 둔 것부터 이어 간다.
 */
@HiltWorker
class FeatureWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
    private val tripDao: TripDao,
    private val featureDao: MediaFeatureDao,
    private val reader: FeatureReader,
    private val thermal: ThermalGuard,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ids = tripDao.allMedia().filter { !it.video }.map { it.mediaId }.distinct()
        val modified = mediaRepository.photoModified(ids)
        val stamps = featureDao.stamps().associate { it.mediaId to it.modified }
        val todo = ids.filter { id -> modified[id] != null && stamps[id] != modified[id] }

        val batch = mutableListOf<MediaFeatureEntity>()
        todo.forEachIndexed { index, id ->
            ensureActive()
            thermal.pace()
            reader.read(id)?.let { batch += MediaFeatureEntity.of(id, modified.getValue(id), it) }
            if (batch.size == 25 || index == todo.lastIndex) {
                featureDao.upsert(batch.toList())
                batch.clear()
            }
        }
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "media-features"

        /** 새 여행 결과로 다시 시작한다(이미 계산한 사진은 건너뜀) */
        fun start(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

        /** 돌고 있지 않으면 시작한다 — 앱 업데이트 전에 찾은 여행이나 멈춘 계산을 채운다 */
        fun ensure(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, OneTimeWorkRequestBuilder<FeatureWorker>().build())
        }
    }
}
