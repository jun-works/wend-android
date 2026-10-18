package app.wend.sea

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.wend.sea.core.designsystem.theme.WendTheme
import app.wend.sea.data.BestShotSaveMode
import app.wend.sea.data.FavoriteRepository
import app.wend.sea.feature.bestshot.BestShotSaveDialogs
import app.wend.sea.feature.bestshot.BestShotSaveFlow
import app.wend.sea.feature.bestshot.BestShotSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests only a newly generated disposable image, never any existing gallery photo. */
class BestShotFavoriteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemFavoriteApproval() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.wend.sea.screenshots")
        val allowed = InstrumentationRegistry.getArguments().getString("favoriteAction") != "deny"
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "WendFavoriteTest-${System.nanoTime()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WendFavoriteTest/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(240, 180, 60))
            checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val id = ContentUris.parseId(uri)
            val repository = FavoriteRepository(context)
            val mode = MutableStateFlow(BestShotSaveMode.GALLERY)
            lateinit var flow: BestShotSaveFlow
            var saved = 0
            var result: BestShotSaveResult? = null
            compose.setContent {
                val scope = rememberCoroutineScope()
                flow = androidx.compose.runtime.remember {
                    BestShotSaveFlow(scope, mode, { mode.value = it }, repository::notFavoriteIds)
                }
                WendTheme { BestShotSaveDialogs(flow) }
            }
            compose.runOnIdle { flow.submit(listOf(id), { saved++ }, { result = it }) }
            // Only this generated image is shown in the native approval window.
            compose.waitUntil(timeoutMillis = 120_000) { result != null }
            compose.runOnIdle {
                assertEquals(1, saved)
                assertEquals(if (allowed) BestShotSaveResult.FAVORITES_ADDED else BestShotSaveResult.FAVORITES_DECLINED, result)
                assertEquals(BestShotSaveMode.GALLERY, mode.value)
            }
            val favorites = runBlocking { repository.notFavoriteIds(listOf(id)) }
            assertEquals(if (allowed) emptyList<Long>() else listOf(id), favorites)
            if (allowed) {
                compose.runOnIdle { result = null; flow.submit(listOf(id), { saved++ }, { result = it }) }
                compose.waitUntil(10_000) { result != null }
                compose.runOnIdle { assertEquals(BestShotSaveResult.SAVED, result); assertNull(flow.state.value.approval) }
            }
        } finally {
            // Delete only the image inserted above, never search or delete by a broad folder query.
            resolver.delete(uri, null, null)
        }
    }
}
