package app.wend.sea.feature.permission

import app.wend.sea.data.MediaAccess
import app.wend.sea.data.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 권한 안내창 결과를 기록한다. 권한 화면들(안내 · 일부 허용 · 허용 안 됨)이 같이 쓴다. */
class PermissionResultHandler @Inject constructor(
    private val userRepository: UserRepository,
) {
    /**
     * @param showRationale 결과 직후 `shouldShowRequestPermissionRationale` 값.
     *   한 번 이상 거절한 뒤 이 값이 false 면 Android 가 더는 안내창을 띄우지 않는다(두 번째 거절).
     */
    suspend fun record(access: MediaAccess, showRationale: Boolean) {
        if (access == MediaAccess.None) {
            val denied = userRepository.mediaDenials.first()
            userRepository.recordMediaDenial(settingsOnly = denied >= 1 && !showRationale)
        } else {
            userRepository.resetMediaDenials()
        }
    }
}
