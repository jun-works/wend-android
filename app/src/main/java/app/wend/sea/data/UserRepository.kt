package app.wend.sea.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    private companion object {
        val KEY_NICKNAME = stringPreferencesKey("nickname")
    }
}
