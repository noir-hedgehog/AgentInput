package com.yuyan.imemodule.context

import java.util.concurrent.atomic.AtomicReference

object A11yScreenshotStore {
    private val imageBytesRef = AtomicReference<ByteArray?>(null)

    fun update(imageBytes: ByteArray?) {
        imageBytesRef.set(imageBytes)
    }

    fun latest(): ByteArray? = imageBytesRef.get()
}
