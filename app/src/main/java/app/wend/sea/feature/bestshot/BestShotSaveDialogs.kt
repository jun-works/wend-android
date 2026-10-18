package app.wend.sea.feature.bestshot

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.R
import app.wend.sea.data.BestShotSaveMode
import app.wend.sea.data.MediaThumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 화면을 닫기 전에 시스템 승인을 마친다. 이미 발행한 요청은 회전 시 다시 띄우지 않는다. */
@Composable
fun BestShotSaveDialogs(flow: BestShotSaveFlow) {
    val state by flow.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        flow.approvalResult(it.resultCode == Activity.RESULT_OK)
    }
    val request = state.approval
    LaunchedEffect(request?.token) {
        if (request == null || request.launched) return@LaunchedEffect
        try {
            val sender = withContext(Dispatchers.IO) {
                MediaStore.createFavoriteRequest(
                    context.contentResolver,
                    request.ids.map { MediaThumbnail(it, false).uri },
                    true,
                ).intentSender
            }
            flow.approvalLaunched(request.token)
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { flow.approvalResult(allowed = false, failed = true) }
    }
    if (state.choosingMode) {
        BestShotModeDialog(BestShotSaveMode.UNSET, flow::selectMode, flow::dismissMode)
    } else if (state.busy) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.best_save_processing), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
