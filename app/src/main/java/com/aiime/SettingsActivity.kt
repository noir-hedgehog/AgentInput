package com.aiime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiime.util.SettingsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager(this)

        // 第一步：启用 IME
        findViewById<Button>(R.id.btn_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // 第二步：管理 AI 引擎
        findViewById<Button>(R.id.btn_config_ai).setOnClickListener {
            startActivity(Intent(this, AiProvidersActivity::class.java))
        }

        // 第三步（可选）：无障碍服务
        findViewById<Button>(R.id.btn_enable_a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 知识库
        findViewById<Button>(R.id.btn_manage_kb).setOnClickListener {
            startActivity(Intent(this, KnowledgeBaseActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateA11yStatus()
        updateActiveProviderSummary()
    }

    private fun updateActiveProviderSummary() {
        lifecycleScope.launch {
            settingsManager.allProviderConfigsFlow.collect { configs ->
                val active = configs.firstOrNull { it.isActive && it.isConfigured }
                val tv = findViewById<TextView>(R.id.tv_active_provider_summary)
                tv?.text = if (active != null) {
                    "${active.displayName} · ${active.modelId}"
                } else {
                    "未配置"
                }
            }
        }
    }

    private fun updateA11yStatus() {
        val enabled = isA11yServiceEnabled()
        val tv = findViewById<TextView>(R.id.tv_a11y_status)
        tv?.text = if (enabled) "● 已启用" else "○ 未启用"
        tv?.setTextColor(
            if (enabled) getColor(R.color.ime_accent)
            else getColor(R.color.ime_scene_tag_bg)
        )
    }

    private fun isA11yServiceEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            if (splitter.next().equals(
                    "${packageName}/.accessibility.AiAccessibilityService",
                    ignoreCase = true
                )
            ) return true
        }
        return false
    }
}
