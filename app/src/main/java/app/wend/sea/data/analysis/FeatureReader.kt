package app.wend.sea.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import app.wend.sea.data.MediaThumbnail
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 한 장의 dHash·밝기 값을 읽는다 — design/BEST_SHOT.md.
 * 시스템 썸네일만 쓰고 원본은 열지 않는다. 9×8 칸 dHash 와 밝기에는 작은 썸네일로 충분하다.
 */
@Singleton
class FeatureReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 읽을 수 없으면(지워짐 등) null */
    fun read(mediaId: Long): PhotoQuality? {
        val thumb = runCatching {
            context.contentResolver.loadThumbnail(MediaThumbnail(mediaId, video = false).uri, Size(THUMB_PX, THUMB_PX), null)
        }.getOrNull() ?: return null
        try {
            if (thumb.width < 9 || thumb.height < 8) return null
            return PhotoQuality.of(luma(thumb), thumb.width, thumb.height)
        } finally {
            thumb.recycle()
        }
    }

    private fun luma(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        return pixels
    }

    private companion object {
        const val THUMB_PX = 256
    }
}
