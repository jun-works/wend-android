package app.wend.sea.feature.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.wend.sea.data.MediaAccess
import app.wend.sea.data.MediaPermissions

/** 권한 요청과 설정 이동을 화면에 붙이는 도구. 결과 판단은 ViewModel 이 한다. */
class MediaPermissionRequest internal constructor(
    val request: () -> Unit,
    val openSettings: () -> Unit,
    /**
     * 다시 요청하면 시스템 안내창이 뜨는지(`shouldShowRequestPermissionRationale`).
     * 일부 허용 상태에서 false 면 안내창 없이 사진 고르는 화면만 뜬다.
     */
    val canShowDialog: () -> Boolean,
)

/**
 * @param onResult 안내창 결과: 현재 접근 상태와, 다시 물을 수 있는지(`shouldShowRequestPermissionRationale`)
 * @param onReturnFromSettings 앱 권한 설정에 갔다가 돌아왔을 때의 접근 상태
 */
@Composable
fun rememberMediaPermissionRequest(
    onResult: (access: MediaAccess, showRationale: Boolean) -> Unit,
    onReturnFromSettings: (MediaAccess) -> Unit,
): MediaPermissionRequest {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnReturn by rememberUpdatedState(onReturnFromSettings)
    var inSettings by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, MediaPermissions.primary) } ?: false
        currentOnResult(MediaPermissions.currentAccess(context), rationale)
    }

    LifecycleResumeEffect(Unit) {
        if (inSettings) {
            inSettings = false
            currentOnReturn(MediaPermissions.currentAccess(context))
        }
        onPauseOrDispose { }
    }

    return MediaPermissionRequest(
        request = { launcher.launch(MediaPermissions.request) },
        openSettings = {
            inSettings = true
            context.openAppSettings()
        },
        canShowDialog = {
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, MediaPermissions.primary) } ?: false
        },
    )
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
