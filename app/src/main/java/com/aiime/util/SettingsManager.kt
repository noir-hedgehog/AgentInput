package com.aiime.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiime.engine.AiProviderConfig
import com.aiime.engine.AiProviderType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aiime_settings")

class SettingsManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        // 每个 provider 的配置单独存一个 key，key = "provider_<type>"
        private fun providerKey(type: AiProviderType) =
            stringPreferencesKey("provider_${type.name.lowercase()}")

        // 当前激活的 provider 类型
        private val KEY_ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
    }

    // ---- 读取 ----

    /** 读取指定 provider 的配置（Flow） */
    fun providerConfigFlow(type: AiProviderType): Flow<AiProviderConfig> =
        context.dataStore.data.map { prefs ->
            prefs[providerKey(type)]?.let { json ->
                runCatching { gson.fromJson(json, AiProviderConfig::class.java) }.getOrNull()
            } ?: AiProviderConfig(type)
        }

    /** 读取所有 provider 配置（Flow） */
    val allProviderConfigsFlow: Flow<List<AiProviderConfig>> =
        context.dataStore.data.map { prefs ->
            val activeType = prefs[KEY_ACTIVE_PROVIDER]
                ?.let { runCatching { AiProviderType.valueOf(it) }.getOrNull() }
                ?: AiProviderType.ANTHROPIC
            AiProviderType.entries.map { type ->
                prefs[providerKey(type)]
                    ?.let { json -> runCatching { gson.fromJson(json, AiProviderConfig::class.java) }.getOrNull() }
                    ?: AiProviderConfig(type)
            }.map { cfg -> cfg.copy(isActive = cfg.type == activeType) }
        }

    /** 同步读取当前激活的 provider 配置（供 IME 服务启动时调用） */
    fun getActiveProviderSync(): AiProviderConfig = runBlocking {
        val prefs = context.dataStore.data.first()
        val activeType = prefs[KEY_ACTIVE_PROVIDER]
            ?.let { runCatching { AiProviderType.valueOf(it) }.getOrNull() }
            ?: AiProviderType.ANTHROPIC
        prefs[providerKey(activeType)]
            ?.let { json -> runCatching { gson.fromJson(json, AiProviderConfig::class.java) }.getOrNull() }
            ?: AiProviderConfig(activeType)
    }

    // ---- 写入 ----

    /** 保存某个 provider 的配置 */
    suspend fun saveProviderConfig(config: AiProviderConfig) {
        context.dataStore.edit { prefs ->
            prefs[providerKey(config.type)] = gson.toJson(config)
        }
    }

    /** 设置当前激活的 provider（同时保存其配置） */
    suspend fun setActiveProvider(config: AiProviderConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROVIDER] = config.type.name
            prefs[providerKey(config.type)] = gson.toJson(config)
        }
    }

    // ---- 兼容旧版（迁移用）----

    /** @deprecated 使用 getActiveProviderSync() */
    fun getApiKeySync(): String = getActiveProviderSync().apiKey
}
