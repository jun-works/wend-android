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
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.notif_channel_progress_description)
                },
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.notif_channel_done_description)
                },
            ),
        )
    }

    fun progressInfo(done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_wend)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(
                if (total > 0) context.getString(R.string.notif_progress_count, format(done), format(total))
                else context.getString(R.string.notif_progress_text),
            )
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
            .setContentTitle(
                if (tripCount > 0) context.resources.getQuantityString(R.plurals.notif_done_title, tripCount, tripCount)
                else context.getString(R.string.notif_none_title),
            )
            .setContentText(context.getString(if (tripCount > 0) R.string.notif_done_text else R.string.notif_none_text))
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
