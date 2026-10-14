package app.wend.sea.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user")

/** 닉네임 등 사용자 정보. 기기 안(DataStore)에만 저장한다. */
@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.userDataStore

    val nickname: Flow<String?> = dataStore.data.map { it[KEY_NICKNAME] }

    suspend fun saveNickname(nickname: String) {
        dataStore.edit { it[KEY_NICKNAME] = nickname }
    }

    /** 틱·핀 안내까지 마쳤는지. 마쳤으면 다음 실행부터 사진 접근 단계로 바로 간다. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    /** 사진 접근 권한을 거절한 횟수 */
    val mediaDenials: Flow<Int> = dataStore.data.map { it[KEY_MEDIA_DENIALS] ?: 0 }

    /** Android 가 더는 권한 안내창을 띄우지 않아 설정에서만 허용할 수 있는 상태 */
    val mediaSettingsOnly: Flow<Boolean> = dataStore.data.map { it[KEY_MEDIA_SETTINGS_ONLY] ?: false }

    suspend fun recordMediaDenial(settingsOnly: Boolean) {
        dataStore.edit {
            it[KEY_MEDIA_DENIALS] = (it[KEY_MEDIA_DENIALS] ?: 0) + 1
            it[KEY_MEDIA_SETTINGS_ONLY] = settingsOnly
        }
    }

    suspend fun resetMediaDenials() {
        dataStore.edit {
            it.remove(KEY_MEDIA_DENIALS)
            it.remove(KEY_MEDIA_SETTINGS_ONLY)
        }
    }

    private companion object {
        val KEY_NICKNAME = stringPreferencesKey("nickname")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_MEDIA_DENIALS = intPreferencesKey("media_denials")
        val KEY_MEDIA_SETTINGS_ONLY = booleanPreferencesKey("media_settings_only")
    }
}
