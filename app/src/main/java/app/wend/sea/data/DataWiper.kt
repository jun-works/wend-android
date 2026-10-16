package app.wend.sea.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import app.wend.sea.data.local.WendDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시안 ⑲ Wend 데이터 지우기: 찾은 여행, 베스트샷, 직접 넣고 뺀 기록, 사진 값, 닉네임을 지우고 처음 화면으로 돌아갈 준비를 한다.
 * 기기의 사진은 건드리지 않는다.
 */
@Singleton
class DataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: WendDatabase,
    private val userRepository: UserRepository,
) {
    suspend fun wipe() {
        // 돌고 있는 여행 찾기·사진 값 계산부터 멈춘다 — 지운 뒤에 다시 써 넣지 않게
        WorkManager.getInstance(context).cancelAllWork()
        NotificationManagerCompat.from(context).cancelAll()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        userRepository.clearAll()
    }
}
