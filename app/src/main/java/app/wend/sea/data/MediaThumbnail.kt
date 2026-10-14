package app.wend.sea.data

import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentUris

/** 기기 사진·영상 한 개의 썸네일 — Coil 로 그린다 */
data class MediaThumbnail(val mediaId: Long, val video: Boolean) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaId,
        )
}

/**
 * Android 가 만들어 두는 썸네일(`ContentResolver.loadThumbnail`)을 쓴다.
 * 원본을 열어 줄이지 않아 빠르고, 영상도 같은 방법으로 첫 장면을 얻는다.
 */
class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOrElse { DEFAULT_PX }
        val height = options.size.height.pxOrElse { DEFAULT_PX }
        val bitmap = options.context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_PX = 512
    }
}
