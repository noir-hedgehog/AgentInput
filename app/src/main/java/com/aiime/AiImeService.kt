package com.aiime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiime.accessibility.AccessibilityContextBridge
import com.aiime.accessibility.ScreenContext
import com.aiime.data.AppDatabase
import com.aiime.data.KnowledgeEntry
import com.aiime.engine.AiEngine
import com.aiime.engine.AiEngineFactory
import com.aiime.engine.AiProviderConfig
import com.aiime.engine.ContextAnalyzer
import com.aiime.engine.ConversationMessage
import com.aiime.engine.SceneContext
import com.aiime.ui.ConversationAdapter
import com.aiime.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 增强输入法服务（多轮对话 + AccessibilityService 场景感知）
 *
 * 新增能力：
 * 1. 从 AccessibilityContextBridge 读取屏幕上下文，注入 AI System Prompt
 * 2. 维护 conversationHistory 列表，支持多轮追问
 * 3. 气泡 UI：历史消息展示在 RecyclerView 中
 * 4. 点击旧的 AI 气泡可一键重新注入
 */
class AiImeService : android.inputmethodservice.InputMethodService() {

    companion object {
        private const val TAG = "AiImeService"
    }

    // ---- 核心组件 ----
    private lateinit var contextAnalyzer: ContextAnalyzer
    private lateinit var settingsManager: SettingsManager
    private lateinit var database: AppDatabase

    // ---- 协程 ----
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var generationJob: Job? = null

    // ---- UI 引用 ----
    private lateinit var tvSceneLabel: TextView
    private lateinit var tvA11yStatus: TextView
    private lateinit var btnClearConversation: ImageButton
    private lateinit var btnToggleAi: ImageButton
    private lateinit var aiCollapsible: LinearLayout
    private lateinit var rvConversation: RecyclerView
    private lateinit var generatingIndicator: LinearLayout
    private lateinit var tvAiStreaming: TextView
    private lateinit var actionButtons: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnSend: Button
    private lateinit var etAiInput: EditText
    private lateinit var btnGenerate: Button

    // ---- 对话 ----
    private lateinit var conversationAdapter: ConversationAdapter

    // ---- 状态 ----
    private var currentScene: SceneContext = SceneContext()
    private var currentPackage: String = ""
    private var isAiPanelExpanded = true

    /** 本轮会话历史（切换 App 或手动清除时重置） */
    private val conversationHistory = mutableListOf<ConversationMessage>()

    /** 当前流式输出的临时文字 */
    private var streamingText = ""

    /** 最新一次 AI 完整回复 */
    private var lastGeneratedText = ""

    override fun onCreate() {
        super.onCreate()
        contextAnalyzer = ContextAnalyzer(this)
        settingsManager = SettingsManager(this)
        database = AppDatabase.getInstance(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.ime_main, null)
        bindViews(view)
        setupAdapter(view)
        setupListeners()
        return view
    }

    private fun bindViews(view: View) {
        tvSceneLabel = view.findViewById(R.id.tv_scene_label)
        tvA11yStatus = view.findViewById(R.id.tv_a11y_status)
        btnClearConversation = view.findViewById(R.id.btn_clear_conversation)
        btnToggleAi = view.findViewById(R.id.btn_toggle_ai)
        aiCollapsible = view.findViewById(R.id.ai_collapsible)
        rvConversation = view.findViewById(R.id.rv_conversation)
        generatingIndicator = view.findViewById(R.id.generating_indicator)
        tvAiStreaming = view.findViewById(R.id.tv_ai_streaming)
        actionButtons = view.findViewById(R.id.action_buttons)
        btnRetry = view.findViewById(R.id.btn_retry)
        btnSend = view.findViewById(R.id.btn_send)
        etAiInput = view.findViewById(R.id.et_ai_input)
        btnGenerate = view.findViewById(R.id.btn_generate)
    }

    private fun setupAdapter(view: View) {
        conversationAdapter = ConversationAdapter(
            onAssistantBubbleClick = { text ->
                // 点击历史 AI 气泡 → 直接重新注入该内容
                commitText(text)
            }
        )
        rvConversation.adapter = conversationAdapter
        rvConversation.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 最新消息在底部
        }
    }

    private fun setupListeners() {
        btnGenerate.setOnClickListener {
            val intent = etAiInput.text?.toString()?.trim() ?: ""
            if (intent.isNotEmpty()) startGeneration(intent)
        }

        btnSend.setOnClickListener {
            if (lastGeneratedText.isNotEmpty()) {
                commitText(lastGeneratedText)
            }
        }

        btnRetry.setOnClickListener {
            // 移除最后一组 user+assistant，重新生成
            if (conversationHistory.size >= 2) {
                // 保留 user 意图重试
                val lastUserMsg = conversationHistory
                    .lastOrNull { it.isUser }?.content ?: ""
                // 剪掉最后一轮
                while (conversationHistory.isNotEmpty() &&
                    conversationHistory.last().isAssistant) {
                    conversationHistory.removeAt(conversationHistory.lastIndex)
                }
                if (conversationHistory.isNotEmpty() &&
                    conversationHistory.last().isUser) {
                    conversationHistory.removeAt(conversationHistory.lastIndex)
                }
                syncConversationUI()
                if (lastUserMsg.isNotEmpty()) startGeneration(lastUserMsg)
            } else {
                // 第一轮重试
                val intent = etAiInput.text?.toString()?.trim() ?: ""
                if (intent.isNotEmpty()) startGeneration(intent)
            }
        }

        btnClearConversation.setOnClickListener {
            clearConversation()
        }

        btnToggleAi.setOnClickListener {
            toggleAiPanel()
        }
    }

    // ---- IME 生命周期 ----

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)

        val newPackage = editorInfo.packageName ?: ""

        // 切换了 App → 重置对话
        if (newPackage != currentPackage && currentPackage.isNotEmpty()) {
            clearConversation()
        }
        currentPackage = newPackage

        currentScene = contextAnalyzer.analyze(editorInfo, currentPackage)
        Log.d(TAG, "Scene: ${currentScene.sceneType.label} pkg=$currentPackage")

        updateSceneLabel()
        updateA11yStatusDot()
        resetStreamingArea()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        generationJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ---- 核心：启动 AI 生成 ----

    private fun startGeneration(userIntent: String) {
        generationJob?.cancel()
        showGenerating()

        generationJob = serviceScope.launch {
            val knowledgeEntries = fetchRelevantKnowledge(userIntent)
            val providerConfig: AiProviderConfig = settingsManager.getActiveProviderSync()

            if (!providerConfig.isConfigured) {
                withContext(Dispatchers.Main) {
                    handleGenerationEvent(
                        AiEngine.GenerationEvent.Error("请先在设置中配置 ${providerConfig.displayName} API Key"),
                        userIntent
                    )
                }
                return@launch
            }

            val existingText = currentInputConnection
                ?.getTextBeforeCursor(500, 0)?.toString() ?: ""
            currentScene = currentScene.copy(existingText = existingText)

            val screenCtx: ScreenContext? = AccessibilityContextBridge.current()
                .takeIf { !it.isEmpty && it.packageName == currentPackage }

            val historySnapshot = conversationHistory.toList()
            val engine = AiEngineFactory.create(providerConfig)

            engine.generateStream(
                userIntent = userIntent,
                sceneContext = currentScene,
                screenContext = screenCtx,
                knowledgeEntries = knowledgeEntries,
                conversationHistory = historySnapshot,
                config = providerConfig
            ).collect { event ->
                withContext(Dispatchers.Main) {
                    handleGenerationEvent(event, userIntent)
                }
            }
        }
    }

    private fun handleGenerationEvent(event: AiEngine.GenerationEvent, userIntent: String) {
        when (event) {
            is AiEngine.GenerationEvent.Start -> {
                streamingText = ""
                tvAiStreaming.text = ""
                tvAiStreaming.visibility = View.VISIBLE
                actionButtons.visibility = View.GONE
            }

            is AiEngine.GenerationEvent.Delta -> {
                streamingText += event.text
                tvAiStreaming.text = streamingText
            }

            is AiEngine.GenerationEvent.Complete -> {
                lastGeneratedText = event.fullText

                // 将本轮 user + assistant 记入历史
                conversationHistory.add(ConversationMessage("user", userIntent))
                conversationHistory.add(ConversationMessage("assistant", event.fullText))

                // 更新气泡列表
                syncConversationUI()

                // 隐藏流式文本，显示操作按钮
                tvAiStreaming.visibility = View.GONE
                hideGenerating()
                actionButtons.visibility = View.VISIBLE
            }

            is AiEngine.GenerationEvent.Error -> {
                tvAiStreaming.text = "生成失败：${event.message}"
                hideGenerating()
                actionButtons.visibility = View.VISIBLE
            }
        }
    }

    // ---- 文字注入目标 App 输入框 ----

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.commitText(text, 1)
        ic.endBatchEdit()
        Log.d(TAG, "Committed: ${text.take(40)}…")

        // 注入后收起操作栏，但保留对话历史
        actionButtons.visibility = View.GONE
        tvAiStreaming.visibility = View.GONE
        etAiInput.text?.clear()
    }

    // ---- 知识库查询 ----

    private suspend fun fetchRelevantKnowledge(userIntent: String): List<KnowledgeEntry> =
        withContext(Dispatchers.IO) {
            val dao = database.knowledgeDao()
            val sceneKey = currentScene.sceneType.sceneKey
            val byKeyword = dao.searchEntries(sceneKey, userIntent.take(20), limit = 3)
            val byScene = dao.getEntriesByScene(sceneKey, limit = 2)
            (byKeyword + byScene).distinctBy { it.id }.take(5)
        }

    // ---- 对话管理 ----

    private fun clearConversation() {
        conversationHistory.clear()
        lastGeneratedText = ""
        syncConversationUI()
        resetStreamingArea()
        etAiInput.text?.clear()
        btnClearConversation.visibility = View.GONE
    }

    private fun syncConversationUI() {
        conversationAdapter.submitMessages(conversationHistory.toList())
        if (conversationHistory.isNotEmpty()) {
            rvConversation.visibility = View.VISIBLE
            btnClearConversation.visibility = View.VISIBLE
            rvConversation.scrollToPosition(conversationAdapter.itemCount - 1)
        } else {
            rvConversation.visibility = View.GONE
            btnClearConversation.visibility = View.GONE
        }
    }

    // ---- UI 状态 ----

    private fun updateSceneLabel() {
        val providerName = settingsManager.getActiveProviderSync().displayName
        val sceneLabel = buildString {
            if (currentScene.appName.isNotEmpty()) append("${currentScene.appName} · ")
            append(currentScene.sceneType.label)
        }
        tvSceneLabel.text = "[$providerName] $sceneLabel"
    }

    private fun updateA11yStatusDot() {
        val a11yEnabled = isAccessibilityServiceEnabled()
        tvA11yStatus.text = "●"
        tvA11yStatus.setTextColor(
            if (a11yEnabled) 0xFF89B4FA.toInt()   // 蓝色：已启用
            else 0xFF45475A.toInt()                 // 灰色：未启用
        )
        tvA11yStatus.contentDescription =
            if (a11yEnabled) "屏幕感知：已启用" else "屏幕感知：未启用，可在设置中开启"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }
        if (accessibilityEnabled != 1) return false

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val component = colonSplitter.next()
            if (component.equals(
                    "${packageName}/.accessibility.AiAccessibilityService",
                    ignoreCase = true
                )
            ) return true
        }
        return false
    }

    private fun showGenerating() {
        generatingIndicator.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        actionButtons.visibility = View.GONE
    }

    private fun hideGenerating() {
        generatingIndicator.visibility = View.GONE
        btnGenerate.isEnabled = true
    }

    private fun resetStreamingArea() {
        tvAiStreaming.visibility = View.GONE
        tvAiStreaming.text = ""
        actionButtons.visibility = View.GONE
        streamingText = ""
        hideGenerating()
    }

    private fun toggleAiPanel() {
        isAiPanelExpanded = !isAiPanelExpanded
        aiCollapsible.visibility = if (isAiPanelExpanded) View.VISIBLE else View.GONE
        btnToggleAi.setImageResource(
            if (isAiPanelExpanded) android.R.drawable.arrow_up_float
            else android.R.drawable.arrow_down_float
        )
    }
}
