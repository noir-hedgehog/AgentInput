package com.yuyan.imemodule.context

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

data class EditorContext(
    val inputType: Int,
    val imeAction: Int,
    val fieldHint: String?,
)

data class InputTextContext(
    val beforeCursor: String,
    val selectedText: String?,
    val afterCursor: String,
)

data class UiSummary(
    val packageName: String?,
    val visibleTextSummary: String?,
    val captureTimeMs: Long,
)

data class ContextSnapshot(
    val packageName: String?,
    val editorInfo: EditorContext,
    val inputText: InputTextContext,
    val uiSummary: UiSummary?,
    val screenshotSummary: String?,
    val timestampMs: Long,
)

interface ContextCollector {
    fun collect(inputConnection: InputConnection?, editorInfo: EditorInfo?): ContextSnapshot
}

class ImeContextCollector : ContextCollector {
    override fun collect(inputConnection: InputConnection?, editorInfo: EditorInfo?): ContextSnapshot {
        val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0)
        val before = inputConnection?.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        val selected = inputConnection?.getSelectedText(0)?.toString()
        val after = inputConnection?.getTextAfterCursor(60, 0)?.toString().orEmpty()
        val packageName = editorInfo?.packageName?.toString()
        return ContextSnapshot(
            packageName = packageName,
            editorInfo = EditorContext(
                inputType = editorInfo?.inputType ?: 0,
                imeAction = editorInfo?.imeOptions ?: 0,
                fieldHint = editorInfo?.hintText?.toString(),
            ),
            inputText = InputTextContext(
                beforeCursor = if (before.isNotBlank()) before else extracted?.text?.toString().orEmpty(),
                selectedText = selected,
                afterCursor = after,
            ),
            uiSummary = A11ySnapshotStore.currentSnapshot(),
            screenshotSummary = null,
            timestampMs = System.currentTimeMillis(),
        )
    }
}
