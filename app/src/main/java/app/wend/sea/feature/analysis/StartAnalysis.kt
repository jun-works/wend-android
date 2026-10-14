package app.wend.sea.feature.analysis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 여행 찾기를 시작하기 직전에 (Android 13+) 알림 권한을 묻는다 — 진행·완료 알림에 쓴다.
 * 거절해도 분석은 그대로 시작한다.
 */
@Composable
fun rememberStartWithNotificationPermission(start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentStart by rememberUpdatedState(start)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentStart() }
    return {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else currentStart()
    }
}
