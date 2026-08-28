package com.agnesai.chat.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agnesai.chat.data.auth.AuthState
import com.agnesai.chat.data.auth.AuthStorage
import com.agnesai.chat.data.auth.UserInfo
import com.agnesai.chat.data.network.MODEL_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.content.Context

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 聊天模型与生成参数的全局配置。默认值保持与硬编码行为一致。 */
data class ChatSettings(
    val modelName: String = MODEL_NAME,
    val temperature: Float = 1f,
    val topP: Float = 1f,
    val maxTokens: Int? = null
)

class SettingsDataStore(private val context: Context) : AuthStorage {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
        private val KEY_READ_ANNOUNCEMENTS = stringSetPreferencesKey("read_announcements")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        private val KEY_TOP_P = floatPreferencesKey("top_p")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant."
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map {
        it[KEY_API_KEY].orEmpty()
    }
    val systemPrompt: Flow<String> = context.settingsDataStore.data.map {
        it[KEY_SYSTEM_PROMPT]?.takeIf(String::isNotBlank) ?: DEFAULT_SYSTEM_PROMPT
    }

    /** 聊天模型与生成参数合并读取；非法或缺失值回退默认。 */
    val chatSettings: Flow<ChatSettings> = context.settingsDataStore.data.map { prefs ->
        ChatSettings(
            modelName = prefs[KEY_MODEL_NAME]?.takeIf(String::isNotBlank) ?: MODEL_NAME,
            temperature = prefs[KEY_TEMPERATURE]?.coerceIn(0f, 2f) ?: 1f,
            topP = prefs[KEY_TOP_P]?.coerceIn(0f, 1f) ?: 1f,
            maxTokens = prefs[KEY_MAX_TOKENS]?.takeIf { it > 0 }
        )
    }

    /** 登录状态：由本地 token 是否存在的快照 Flow */
    override val authState: Flow<AuthState> = context.settingsDataStore.data.map { prefs ->
        val token = prefs[KEY_AUTH_TOKEN].orEmpty()
        if (token.isBlank()) {
            AuthState.LoggedOut
        } else {
            AuthState.LoggedIn(
                UserInfo(
                    id = prefs[KEY_USER_ID].orEmpty(),
                    username = prefs[KEY_USERNAME].orEmpty(),
                    nickname = prefs[KEY_NICKNAME].orEmpty().ifEmpty { "User" },
                    avatarUrl = prefs[KEY_AVATAR_URL]?.takeIf(String::isNotBlank)
                )
            )
        }
    }

    /** 已读公告 ID 集合 */
    val readAnnouncements: Flow<Set<String>> = context.settingsDataStore.data.map {
        it[KEY_READ_ANNOUNCEMENTS].orEmpty()
    }

    suspend fun getApiKey(): String = apiKey.first()

    suspend fun getSystemPrompt(): String = systemPrompt.first()

    suspend fun getChatSettings(): ChatSettings = chatSettings.first()

    override suspend fun getAuthToken(): String = context.settingsDataStore.data.map {
        it[KEY_AUTH_TOKEN].orEmpty()
    }.first()

    suspend fun setApiKey(value: String) {
        context.settingsDataStore.edit { it[KEY_API_KEY] = value.trim() }
    }

    suspend fun setSystemPrompt(value: String) {
        context.settingsDataStore.edit { it[KEY_SYSTEM_PROMPT] = value.trim() }
    }

    /** 保存聊天模型与生成参数；越界值 clamp，maxTokens 非法时移除保持未设置。 */
    suspend fun saveChatSettings(
        modelName: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int?
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MODEL_NAME] = modelName.trim().takeIf(String::isNotBlank) ?: MODEL_NAME
            prefs[KEY_TEMPERATURE] = temperature.coerceIn(0f, 2f)
            prefs[KEY_TOP_P] = topP.coerceIn(0f, 1f)
            if (maxTokens != null && maxTokens > 0) {
                prefs[KEY_MAX_TOKENS] = maxTokens
            } else {
                prefs.remove(KEY_MAX_TOKENS)
            }
        }
    }

    override suspend fun saveAuth(token: String, user: UserInfo) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_NICKNAME] = user.nickname
            if (!user.avatarUrl.isNullOrBlank()) {
                prefs[KEY_AVATAR_URL] = user.avatarUrl
            } else {
                prefs.remove(KEY_AVATAR_URL)
            }
        }
    }

    override suspend fun updateProfile(nickname: String, avatarUrl: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = nickname
            if (!avatarUrl.isNullOrBlank()) {
                prefs[KEY_AVATAR_URL] = avatarUrl
            } else {
                prefs.remove(KEY_AVATAR_URL)
            }
        }
    }

    override suspend fun clearAuth() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_NICKNAME)
            prefs.remove(KEY_AVATAR_URL)
        }
    }

    suspend fun isAnnouncementRead(announcementId: String): Boolean =
        readAnnouncements.first().contains(announcementId)

    suspend fun markAnnouncementRead(announcementId: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_READ_ANNOUNCEMENTS].orEmpty().toMutableSet()
            current.add(announcementId)
            prefs[KEY_READ_ANNOUNCEMENTS] = current
        }
    }
}
