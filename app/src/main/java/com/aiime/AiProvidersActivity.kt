package com.aiime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiime.engine.AiEngine
import com.aiime.engine.AiEngineFactory
import com.aiime.engine.AiProviderConfig
import com.aiime.engine.AiProviderType
import com.aiime.engine.ConversationMessage
import com.aiime.engine.SceneContext
import com.aiime.util.SettingsManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AiProvidersActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: ProviderAdapter
    private lateinit var tvActiveHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_providers)

        settingsManager = SettingsManager(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvActiveHint = findViewById(R.id.tv_active_hint)

        adapter = ProviderAdapter(
            onSaveActivate = { config -> saveAndActivate(config) },
            onTest = { config, resultView -> testConnection(config, resultView) }
        )

        val rv = findViewById<RecyclerView>(R.id.rv_providers)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this)

        loadConfigs()
    }

    private fun loadConfigs() {
        lifecycleScope.launch {
            settingsManager.allProviderConfigsFlow.collect { configs ->
                adapter.submitList(configs)
                val active = configs.firstOrNull { it.isActive }
                tvActiveHint.text = if (active != null && active.isConfigured) {
                    "当前激活：${active.displayName}  ·  模型：${active.modelId}"
                } else {
                    "当前未配置任何 AI 引擎，请配置并激活一个"
                }
            }
        }
    }

    private fun saveAndActivate(config: AiProviderConfig) {
        lifecycleScope.launch {
            settingsManager.setActiveProvider(config)
            Toast.makeText(this@AiProvidersActivity,
                "${config.displayName} 已保存并激活", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection(config: AiProviderConfig, resultView: TextView) {
        resultView.visibility = View.VISIBLE
        resultView.text = "连接测试中…"
        resultView.setTextColor(0xFF89B4FA.toInt())

        lifecycleScope.launch {
            val engine = AiEngineFactory.create(config)
            var resultText = ""
            var isError = false

            engine.generateStream(
                userIntent = "请回复「连接成功」这四个字",
                sceneContext = SceneContext(),
                screenContext = null,
                knowledgeEntries = emptyList(),
                conversationHistory = emptyList(),
                config = config
            ).collect { event ->
                when (event) {
                    is AiEngine.GenerationEvent.Complete -> {
                        resultText = "✓ 连接成功：${event.fullText.take(30)}"
                    }
                    is AiEngine.GenerationEvent.Error -> {
                        resultText = "✗ ${event.message}"
                        isError = true
                    }
                    else -> {}
                }
            }

            resultView.text = resultText
            resultView.setTextColor(
                if (isError) 0xFFF38BA8.toInt()   // 红色
                else 0xFFA6E3A1.toInt()             // 绿色
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ---- Adapter ----

class ProviderAdapter(
    private val onSaveActivate: (AiProviderConfig) -> Unit,
    private val onTest: (AiProviderConfig, TextView) -> Unit
) : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

    private var configs: List<AiProviderConfig> = emptyList()
    private val expandedSet = mutableSetOf<AiProviderType>()

    fun submitList(list: List<AiProviderConfig>) {
        configs = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_provider_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(configs[position])
    }

    override fun getItemCount() = configs.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val headerRow: LinearLayout = view.findViewById(R.id.header_row)
        private val tvStatusDot: TextView = view.findViewById(R.id.tv_status_dot)
        private val tvProviderName: TextView = view.findViewById(R.id.tv_provider_name)
        private val tvActiveBadge: TextView = view.findViewById(R.id.tv_active_badge)
        private val ivArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)
        private val expandArea: LinearLayout = view.findViewById(R.id.expand_area)

        // 展开区字段
        private val tilModel: TextInputLayout = view.findViewById(R.id.til_model)
        private val actvModel: AutoCompleteTextView = view.findViewById(R.id.actv_model)
        private val tilCustomModel: TextInputLayout = view.findViewById(R.id.til_custom_model)
        private val etCustomModel: TextInputEditText = view.findViewById(R.id.et_custom_model)
        private val tilBaseUrl: TextInputLayout = view.findViewById(R.id.til_base_url)
        private val etBaseUrl: TextInputEditText = view.findViewById(R.id.et_base_url)
        private val tilApiKey: TextInputLayout = view.findViewById(R.id.til_api_key)
        private val etApiKey: TextInputEditText = view.findViewById(R.id.et_api_key)
        private val etMaxTokens: TextInputEditText = view.findViewById(R.id.et_max_tokens)
        private val tvGetApiKey: TextView = view.findViewById(R.id.tv_get_api_key)
        private val btnTest: Button = view.findViewById(R.id.btn_test)
        private val btnSaveActivate: Button = view.findViewById(R.id.btn_save_activate)
        private val tvTestResult: TextView = view.findViewById(R.id.tv_test_result)

        fun bind(config: AiProviderConfig) {
            val type = config.type
            tvProviderName.text = type.displayName
            tilApiKey.hint = "API Key  (${type.apiKeyPlaceholder})"

            // 激活状态
            val isActive = config.isActive && config.isConfigured
            tvStatusDot.setTextColor(
                if (isActive) 0xFFA6E3A1.toInt() else 0xFF45475A.toInt()
            )
            tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE

            // 展开状态
            val isExpanded = type in expandedSet
            expandArea.visibility = if (isExpanded) View.VISIBLE else View.GONE
            ivArrow.setImageResource(
                if (isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )

            // 点击标题行展开/折叠
            headerRow.setOnClickListener {
                if (type in expandedSet) expandedSet.remove(type) else expandedSet.add(type)
                notifyItemChanged(adapterPosition)
            }

            if (!isExpanded) return

            // ---- 填充展开区 ----

            // 模型下拉（预设）
            val presets = type.presetModels
            if (presets.isNotEmpty()) {
                tilModel.visibility = View.VISIBLE
                val modelLabels = presets.map { it.label }
                val adapter = ArrayAdapter(itemView.context,
                    android.R.layout.simple_dropdown_item_1line, modelLabels)
                actvModel.setAdapter(adapter)
                // 回显当前选中
                val current = presets.indexOfFirst { it.id == config.modelId }
                    .takeIf { it >= 0 } ?: 0
                actvModel.setText(presets.getOrNull(current)?.label ?: "", false)

                // CUSTOM：显示自定义模型输入框
                tilCustomModel.visibility = View.GONE
            } else {
                // CUSTOM provider：没有预设，显示手填模型框
                tilModel.visibility = View.GONE
                tilCustomModel.visibility = View.VISIBLE
                etCustomModel.setText(config.modelId)
            }

            // Base URL（仅 CUSTOM）
            tilBaseUrl.visibility = if (type == AiProviderType.CUSTOM) View.VISIBLE else View.GONE
            if (type == AiProviderType.CUSTOM) etBaseUrl.setText(config.customBaseUrl)

            // API Key
            if (config.apiKey.isNotEmpty()) etApiKey.setText(config.apiKey)

            // Max Tokens
            etMaxTokens.setText(config.maxTokens.toString())

            // 获取 API Key 链接
            if (type.docUrl.isNotEmpty()) {
                tvGetApiKey.visibility = View.VISIBLE
                tvGetApiKey.setOnClickListener {
                    it.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(type.docUrl))
                    )
                }
            } else {
                tvGetApiKey.visibility = View.GONE
            }

            tvTestResult.visibility = View.GONE

            // 测试按钮
            btnTest.setOnClickListener {
                val cfg = collectConfig(config) ?: return@setOnClickListener
                onTest(cfg, tvTestResult)
            }

            // 保存并激活
            btnSaveActivate.setOnClickListener {
                val cfg = collectConfig(config) ?: return@setOnClickListener
                onSaveActivate(cfg)
            }
        }

        /** 从 UI 字段收集 config，验证必填项 */
        private fun collectConfig(original: AiProviderConfig): AiProviderConfig? {
            val type = original.type
            val apiKey = etApiKey.text?.toString()?.trim() ?: ""
            if (apiKey.isEmpty()) {
                etApiKey.error = "请输入 API Key"
                return null
            }

            val modelId = if (type.presetModels.isNotEmpty()) {
                // 从下拉选中项反查 id
                val label = actvModel.text?.toString() ?: ""
                type.presetModels.firstOrNull { it.label == label }?.id
                    ?: type.presetModels.firstOrNull()?.id ?: ""
            } else {
                etCustomModel.text?.toString()?.trim() ?: ""
            }

            val baseUrl = if (type == AiProviderType.CUSTOM) {
                etBaseUrl.text?.toString()?.trim() ?: ""
            } else {
                type.defaultBaseUrl
            }

            if (type == AiProviderType.CUSTOM && baseUrl.isEmpty()) {
                etBaseUrl.error = "请输入 Base URL"
                return null
            }

            val maxTokens = etMaxTokens.text?.toString()?.toIntOrNull() ?: 1024

            return original.copy(
                apiKey = apiKey,
                modelId = modelId,
                customBaseUrl = baseUrl,
                maxTokens = maxTokens.coerceIn(64, 8192)
            )
        }
    }
}
