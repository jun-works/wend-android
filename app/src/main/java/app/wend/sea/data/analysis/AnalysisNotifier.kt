package app.wend.sea.data.analysis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.wend.sea.MainActivity
import app.wend.sea.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/** 여행 찾기 진행 알림(포그라운드)과 완료 알림 */
@Singleton
class AnalysisNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, "여행 찾는 중", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "여행을 찾는 동안 진행 상황을 보여 줘요"
                },
                NotificationChannel(CHANNEL_DONE, "여행 찾기 완료", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "여행을 다 찾으면 알려 드려요"
                },
            ),
        )
    }

    fun progressInfo(done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_wend)
            .setContentTitle("여행을 찾고 있어요")
            .setContentText(if (total > 0) "사진 ${format(done)} / ${format(total)} · 앱을 닫아도 계속돼요" else "앱을 닫아도 계속돼요")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()
        return ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /** 새로 찾기 시작하면 지난 결과 알림은 치운다 */
    fun cancelDone() = manager.cancel(ID_DONE)

    fun notifyDone(tripCount: Int) {
        if (!canNotify()) return
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_wend)
            .setContentTitle(if (tripCount > 0) "여행 ${tripCount}개를 찾았어요" else "아직 여행을 찾지 못했어요")
            .setContentText(if (tripCount > 0) "눌러서 찾은 여행을 확인해 보세요" else "눌러서 이유를 확인해 보세요")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        @Suppress("MissingPermission") // canNotify() 로 확인
        manager.notify(ID_DONE, notification)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun format(n: Int) = NumberFormat.getIntegerInstance().format(n)

    private companion object {
        const val CHANNEL_PROGRESS = "analysis_progress"
        const val CHANNEL_DONE = "analysis_done"
        const val ID_PROGRESS = 1001
        const val ID_DONE = 1002
    }
}
