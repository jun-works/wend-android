package app.wend.sea.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.wend.sea.data.local.MediaLocationDao
import app.wend.sea.data.local.MediaLocationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class MediaCounts(val photos: Int, val videos: Int)

/** 기기의 사진·영상을 MediaStore 로 읽는다. 앱이 볼 수 있는 범위(전체/고른 것)만 보인다. */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: MediaLocationDao,
) {
    fun currentAccess(): MediaAccess = MediaPermissions.currentAccess(context)

    suspend fun counts(): MediaCounts = withContext(Dispatchers.IO) {
        MediaCounts(
            photos = count(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            videos = count(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
        )
    }

    /**
     * 위치(GPS)가 남아 있는 사진·영상 수. MediaStore 에는 위치가 없어서 파일을 열어 읽어야 하므로,
     * 읽은 결과를 DB 에 저장해 두고 새로 생기거나 바뀐 파일만 읽는다.
     * 저장된 결과로 센 수를 먼저 내보내고, 새로 읽는 동안 늘어나는 수를 계속 내보낸다.
     */
    fun locationCount(): Flow<Int> = flow {
        if (!MediaPermissions.canReadLocation(context)) {
            emit(0)
            return@flow
        }
        val visible = items(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false) +
            items(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true)
        val cached = locationDao.getAll().associateBy { it.video to it.mediaId }

        var found = 0
        val toRead = mutableListOf<MediaItem>()
        for (item in visible) {
            val hit = cached[item.video to item.id]
            if (hit != null && hit.modified == item.modified) {
                if (hit.latitude != null) found++
            } else {
                toRead += item
            }
        }
        emit(found)

        val batch = mutableListOf<MediaLocationEntity>()
        toRead.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val location = if (item.video) videoLocation(item.uri) else photoLocation(item.uri)
            if (location != null) found++
            batch += MediaLocationEntity(item.id, item.video, item.modified, location?.first, location?.second)
            if (batch.size == 25 || index == toRead.lastIndex) {
                locationDao.upsert(batch.toList())
                batch.clear()
                emit(found)
            }
        }

        // 모두 볼 수 있을 때만 지워진 파일을 정리한다(일부 허용이면 안 보이는 것과 지워진 것을 구분할 수 없음)
        if (currentAccess() == MediaAccess.All) {
            val visibleKeys = visible.mapTo(HashSet()) { it.video to it.id }
            cached.keys.filterNot { it in visibleKeys }.groupBy({ it.first }, { it.second })
                .forEach { (video, ids) -> ids.chunked(500).forEach { locationDao.delete(video, it) } }
        }
    }.flowOn(Dispatchers.IO)

    private data class MediaItem(val id: Long, val video: Boolean, val modified: Long, val uri: Uri)

    private fun items(collection: Uri, video: Boolean): List<MediaItem> =
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(MediaItem(id, video, cursor.getLong(modifiedColumn), ContentUris.withAppendedId(collection, id)))
                }
            }
        } ?: emptyList()

    private fun count(collection: Uri): Int =
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count } ?: 0

    // setRequireOriginal 없이 열면 Android 10+ 가 GPS 를 지운 채로 준다
    private fun photoLocation(uri: Uri): Pair<Double, Double>? = runCatching {
        context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { stream ->
            ExifInterface(stream).latLong?.let { it[0] to it[1] }
        }
    }.getOrNull()

    private fun videoLocation(uri: Uri): Pair<Double, Double>? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, MediaStore.setRequireOriginal(uri))
                parseIso6709(extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            } finally {
                release()
            }
        }
    }.getOrNull()

    private companion object {
        /** 영상 위치 문자열(ISO 6709, 예: "+37.5665+126.9780/")에서 위도·경도 */
        val ISO_6709 = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

        fun parseIso6709(value: String?): Pair<Double, Double>? {
            val match = ISO_6709.find(value ?: return null) ?: return null
            return match.groupValues[1].toDouble() to match.groupValues[2].toDouble()
        }
    }
}
