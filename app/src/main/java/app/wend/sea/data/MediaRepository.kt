package app.wend.sea.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.wend.sea.data.analysis.MediaPoint
import app.wend.sea.data.local.MediaLocationDao
import app.wend.sea.data.local.MediaLocationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class MediaCounts(val photos: Int, val videos: Int)

/** 위치 읽기 진행: [done] / [total] 개를 봤고 그중 [withLocation] 개에 위치가 있다 */
data class LocationSyncProgress(val done: Int, val total: Int, val withLocation: Int)

/** 기기의 사진·영상을 MediaStore 로 읽는다. 앱이 볼 수 있는 범위(전체/고른 것)만 보인다. */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: MediaLocationDao,
    private val thermal: ThermalGuard,
) {
    /** 앨범 열림 화면과 분석이 동시에 위치를 읽지 않게 — 뒤에 온 쪽은 앞쪽이 채운 저장값을 쓴다 */
    private val syncLock = Mutex()

    fun currentAccess(): MediaAccess = MediaPermissions.currentAccess(context)

    suspend fun counts(): MediaCounts = withContext(Dispatchers.IO) {
        MediaCounts(
            photos = items(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false).size,
            videos = items(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true).size,
        )
    }

    /** 위치가 남아 있는 사진·영상 수. 읽는 동안 늘어나는 수를 계속 내보낸다. */
    fun locationCount(): Flow<Int> = channelFlow {
        syncLocations(allItems()) { send(it.withLocation) }
    }

    /** 분석할 사진·영상 수(캡처 제외) */
    suspend fun analysisItemCount(): Int = withContext(Dispatchers.IO) { allItems().count { !it.screenshot } }

    /**
     * 이미 알고 있는 사진·영상들의 촬영 시각과 저장된 위치(파일은 열지 않는다).
     * 그사이 지워진 것은 빠진다.
     */
    suspend fun points(keys: List<Pair<Boolean, Long>>): List<MediaPoint> = withContext(Dispatchers.IO) {
        val locations = locationDao.getAll().associateBy { it.video to it.mediaId }
        keys.groupBy({ it.first }, { it.second }).flatMap { (video, ids) ->
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ids.chunked(500).flatMap { chunk ->
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_TAKEN),
                    "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                    null,
                    null,
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val taken = if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it > 0 }
                            val location = locations[video to id]
                            add(MediaPoint(id, video, taken, location?.latitude, location?.longitude))
                        }
                    }
                } ?: emptyList()
            }
        }
    }

    /**
     * [keys] 가운데 지금 기기에서 보이는 것 — 휴지통에 있거나 지워진 것은 빠진다.
     * 휴지통에서 되살리면 다시 보이므로, 여행은 이것으로 장수·대표 사진을 센다.
     */
    suspend fun visible(keys: List<Pair<Boolean, Long>>): Set<Pair<Boolean, Long>> = withContext(Dispatchers.IO) {
        keys.groupBy({ it.first }, { it.second }).flatMap { (video, ids) ->
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ids.chunked(500).flatMap { chunk ->
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                    null,
                    null,
                )?.use { cursor ->
                    buildList { while (cursor.moveToNext()) add(video to cursor.getLong(0)) }
                } ?: emptyList()
            }
        }.toSet()
    }

    /**
     * 사진·영상의 화면에 보이는 가로/세로 비율(돌려 찍은 것은 돌린 뒤). 크기를 모르면 빠진다.
     * 크게 보기에서 썸네일(정사각 Crop)이 원본 비율로 자라게 할 때 쓴다.
     */
    suspend fun aspectRatios(keys: List<Pair<Boolean, Long>>): Map<Pair<Boolean, Long>, Float> = withContext(Dispatchers.IO) {
        keys.groupBy({ it.first }, { it.second }).flatMap { (video, ids) ->
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ids.chunked(500).flatMap { chunk ->
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.ORIENTATION),
                    "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                    null,
                    null,
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val w = cursor.getInt(1)
                            val h = cursor.getInt(2)
                            if (w <= 0 || h <= 0) continue
                            val turned = (cursor.getInt(3) / 90) % 2 == 1
                            add((video to cursor.getLong(0)) to if (turned) h.toFloat() / w else w.toFloat() / h)
                        }
                    }
                } ?: emptyList()
            }
        }.toMap()
    }

    /** 사진들의 파일 수정 시각(MediaStore DATE_MODIFIED, 초). 그사이 지워진 것은 빠진다 */
    suspend fun photoModified(ids: List<Long>): Map<Long, Long> = withContext(Dispatchers.IO) {
        ids.chunked(500).flatMap { chunk ->
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED),
                "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})",
                null,
                null,
            )?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1)) }
            } ?: emptyList()
        }.toMap()
    }

    /** 볼 수 있는 사진·영상 전체 수(캡처 포함) — "N개 중 M개가 여행에 담겼어요" 의 N */
    suspend fun visibleCount(): Int = withContext(Dispatchers.IO) { allItems().size }

    /**
     * 분석에 쓸 사진·영상. 캡처는 처음부터 뺀다. 위치는 저장된 값을 쓰고, 새로 생기거나 바뀐 파일만 열어 읽는다.
     */
    suspend fun analysisPoints(onProgress: suspend (LocationSyncProgress) -> Unit): List<MediaPoint> {
        val items = withContext(Dispatchers.IO) { allItems() }.filterNot { it.screenshot }
        val locations = syncLocations(items, onProgress)
        return items.map { item ->
            val location = locations[item.video to item.id]
            MediaPoint(item.id, item.video, item.takenAt, location?.first, location?.second)
        }
    }

    /**
     * [items] 의 위치를 채운다. 저장값이 있으면 그대로, 없거나 파일이 바뀌었으면 열어서 읽고 저장한다.
     * 파일은 한 번에 하나씩 읽고, 기기가 뜨거우면 쉬어 간다.
     */
    private suspend fun syncLocations(
        items: List<MediaItem>,
        onProgress: suspend (LocationSyncProgress) -> Unit,
    ): Map<Pair<Boolean, Long>, Pair<Double, Double>> = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val result = HashMap<Pair<Boolean, Long>, Pair<Double, Double>>()
            if (!MediaPermissions.canReadLocation(context)) {
                onProgress(LocationSyncProgress(items.size, items.size, 0))
                return@withContext result
            }
            val cached = locationDao.getAll().associateBy { it.video to it.mediaId }
            val toRead = mutableListOf<MediaItem>()
            for (item in items) {
                val hit = cached[item.video to item.id]
                if (hit != null && hit.modified == item.modified) {
                    if (hit.latitude != null && hit.longitude != null) result[item.video to item.id] = hit.latitude to hit.longitude
                } else {
                    toRead += item
                }
            }
            var done = items.size - toRead.size
            onProgress(LocationSyncProgress(done, items.size, result.size))

            val batch = mutableListOf<MediaLocationEntity>()
            toRead.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                thermal.pace()
                val location = if (item.video) videoLocation(item.uri) else photoLocation(item.uri)
                if (location != null) result[item.video to item.id] = location
                batch += MediaLocationEntity(item.id, item.video, item.modified, location?.first, location?.second)
                done++
                if (batch.size == 25 || index == toRead.lastIndex) {
                    locationDao.upsert(batch.toList())
                    batch.clear()
                    onProgress(LocationSyncProgress(done, items.size, result.size))
                }
            }

            // 모두 볼 수 있을 때만 지워진 파일을 정리한다(일부 허용이면 안 보이는 것과 지워진 것을 구분할 수 없음)
            if (currentAccess() == MediaAccess.All) {
                val visibleKeys = allItems().mapTo(HashSet()) { it.video to it.id }
                cached.keys.filterNot { it in visibleKeys }.groupBy({ it.first }, { it.second })
                    .forEach { (video, ids) -> ids.chunked(500).forEach { locationDao.delete(video, it) } }
            }
            result
        }
    }

    private class MediaItem(
        val id: Long,
        val video: Boolean,
        val modified: Long,
        val takenAt: Long?,
        val screenshot: Boolean,
        val uri: Uri,
    )

    private fun allItems(): List<MediaItem> =
        items(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false) +
            items(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true)

    private fun items(collection: Uri, video: Boolean): List<MediaItem> =
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        MediaItem(
                            id = id,
                            video = video,
                            modified = cursor.getLong(modifiedColumn),
                            // 촬영 시각만 쓴다. 기기에 등록된 시각(DATE_ADDED)으로 대신하지 않는다.
                            takenAt = if (cursor.isNull(takenColumn)) null else cursor.getLong(takenColumn).takeIf { it > 0 },
                            screenshot = isScreenshot(cursor.getString(pathColumn), cursor.getString(nameColumn)),
                            uri = ContentUris.withAppendedId(collection, id),
                        ),
                    )
                }
            }
        } ?: emptyList()

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

        /** 캡처는 저장 폴더와 파일 이름으로만 판단한다(카메라 정보·화면 크기는 받은 사진도 해당될 수 있어 쓰지 않음) */
        fun isScreenshot(relativePath: String?, displayName: String?): Boolean {
            val path = relativePath.orEmpty().lowercase()
            val name = displayName.orEmpty()
            return path.startsWith("pictures/screenshots") || path.startsWith("dcim/screenshots") ||
                name.startsWith("Screenshot_") || name.startsWith("Screen_Recording_")
        }
    }
}
