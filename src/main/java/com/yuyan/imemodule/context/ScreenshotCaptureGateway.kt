package com.yuyan.imemodule.context

interface ScreenshotCaptureGateway {
    suspend fun captureCurrentScreen(): ByteArray?
}

object NoopScreenshotCaptureGateway : ScreenshotCaptureGateway {
    override suspend fun captureCurrentScreen(): ByteArray? = null
}

object A11yScreenshotCaptureGateway : ScreenshotCaptureGateway {
    override suspend fun captureCurrentScreen(): ByteArray? = A11yScreenshotStore.latest()
}
