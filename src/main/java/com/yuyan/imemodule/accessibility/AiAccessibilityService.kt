package com.yuyan.imemodule.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yuyan.imemodule.context.A11yScreenshotStore
import com.yuyan.imemodule.context.A11ySnapshotStore
import java.io.ByteArrayOutputStream

class AiAccessibilityService : AccessibilityService() {
    private var lastCaptureMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateSnapshot(rootInActiveWindow, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()
        updateSnapshot(rootInActiveWindow, packageName)
    }

    override fun onInterrupt() = Unit

    private fun updateSnapshot(root: AccessibilityNodeInfo?, packageName: String?) {
        if (root == null) return
        val textCollector = ArrayList<String>(32)
        collectTexts(root, textCollector)
        val summary = textCollector
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(20)
            .joinToString(" | ")
        A11ySnapshotStore.update(packageName, summary)
        captureScreenshotIfNeeded()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, acc: MutableList<String>) {
        node.text?.toString()?.let(acc::add)
        node.contentDescription?.toString()?.let(acc::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, acc)
            }
        }
    }

    private fun captureScreenshotIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureMs < 1200) return
        lastCaptureMs = now
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    val jpgBytes = bitmap?.toJpgBytes()
                    bitmap?.recycle()
                    screenshot.hardwareBuffer.close()
                    A11yScreenshotStore.update(jpgBytes)
                }

                override fun onFailure(errorCode: Int) {
                    A11yScreenshotStore.update(null)
                }
            }
        )
    }

    private fun Bitmap.toJpgBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, output)
        return output.toByteArray()
    }
}
