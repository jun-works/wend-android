package app.wend.sea.feature.bestshot

import app.wend.sea.R
import app.wend.sea.core.text.AppText

fun BestShotSaveResult.message(saved: String): String = when (this) {
    BestShotSaveResult.SAVED -> saved
    BestShotSaveResult.FAVORITES_ADDED -> AppText.string(R.string.best_favorite_added)
    BestShotSaveResult.FAVORITES_DECLINED -> AppText.string(R.string.best_favorite_declined)
    BestShotSaveResult.FAVORITES_FAILED -> AppText.string(R.string.best_favorite_failed)
    BestShotSaveResult.SAVE_FAILED -> AppText.string(R.string.best_save_failed)
}
