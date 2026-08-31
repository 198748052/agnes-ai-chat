package com.agnesai.chat.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
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

    suspend fun getApiKey(): String = apiKey.first()

    suspend fun getSystemPrompt(): String = systemPrompt.first()

    suspend fun getChatSettings(): ChatSettings = chatSettings.first()

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
}
