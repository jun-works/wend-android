package app.wend.sea

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.wend.sea.core.designsystem.theme.WendTheme
import app.wend.sea.data.BestShotSaveMode
import app.wend.sea.data.UserRepository
import app.wend.sea.feature.bestshot.BestShotModeDialog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Opt-in isolated emulator UI tests. Never changes the real app or any gallery photos. */
class BestShotUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun isolated() { check(context.packageName == "app.wend.sea.screenshots") }

    @Test fun dialogShowsCurrentChoiceAndAllowsChangingIt() {
        isolated()
        val current = mutableStateOf(BestShotSaveMode.UNSET)
        compose.setContent {
            WendTheme { BestShotModeDialog(current.value, { current.value = it }, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.best_save_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.best_save_gallery)).performClick()
        compose.onNodeWithText(context.getString(R.string.best_save_gallery)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.best_save_current)).assertExists()
        compose.runOnIdle { assertEquals(BestShotSaveMode.GALLERY, current.value) }
        screenshot("best-save-dialog.png")
        compose.onNodeWithText(context.getString(R.string.best_save_app_only)).performClick()
        compose.onNodeWithText(context.getString(R.string.best_save_app_only)).assertIsSelected()
        compose.runOnIdle { assertEquals(BestShotSaveMode.APP_ONLY, current.value) }
    }

    @Test fun largeTextKeepsBothOptionsReachable() {
        isolated()
        var picked = BestShotSaveMode.UNSET
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                WendTheme { BestShotModeDialog(BestShotSaveMode.APP_ONLY, { picked = it }, {}) }
            }
        }
        compose.onNodeWithText(context.getString(R.string.best_save_app_only)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BestShotSaveMode.APP_ONLY, picked) }
        screenshot("best-save-large-text.png")
        compose.onNodeWithText(context.getString(R.string.best_save_gallery)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BestShotSaveMode.GALLERY, picked) }
    }

    @Test fun preferencePersistsAcrossRepositoryInstances() = runBlocking {
        isolated()
        val repository = UserRepository(context)
        val previous = repository.bestShotSaveMode.first()
        try {
            repository.setBestShotSaveMode(BestShotSaveMode.UNSET)
            assertEquals(BestShotSaveMode.UNSET, repository.bestShotSaveMode.first())
            repository.setBestShotSaveMode(BestShotSaveMode.GALLERY)
            assertEquals(BestShotSaveMode.GALLERY, UserRepository(context).bestShotSaveMode.first())
            repository.setBestShotSaveMode(BestShotSaveMode.APP_ONLY)
            assertEquals(BestShotSaveMode.APP_ONLY, UserRepository(context).bestShotSaveMode.first())
        } finally { repository.setBestShotSaveMode(previous) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
