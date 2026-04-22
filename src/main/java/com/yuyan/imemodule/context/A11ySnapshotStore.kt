package com.yuyan.imemodule.context

import java.util.concurrent.atomic.AtomicReference

object A11ySnapshotStore {
    private val snapshotRef = AtomicReference<UiSummary?>(null)

    fun update(packageName: String?, visibleTextSummary: String?) {
        snapshotRef.set(
            UiSummary(
                packageName = packageName,
                visibleTextSummary = visibleTextSummary,
                captureTimeMs = System.currentTimeMillis(),
            )
        )
    }

    fun currentSnapshot(): UiSummary? = snapshotRef.get()
}
