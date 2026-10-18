package app.wend.sea.data

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 사진은 복사·이동하지 않고 현재 즐겨찾기 상태만 읽는다. 추가는 시스템 승인으로만 한다. */
class FavoriteRepository @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun notFavoriteIds(ids: List<Long>): List<Long> = withContext(Dispatchers.IO) {
        ids.distinct().chunked(500).flatMap { batch ->
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_FAVORITE),
                "${MediaStore.MediaColumns._ID} IN (${batch.joinToString(",") { "?" }})",
                batch.map(Long::toString).toTypedArray(),
                null,
            ) ?: error("Unable to read favorite status")
            cursor.use {
                buildList {
                    while (it.moveToNext()) if (it.getInt(1) == 0) add(it.getLong(0))
                }
            }
        }
    }
}
