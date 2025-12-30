package com.example.final_app

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImageAnalysisAnalyzer(
    private val listener: (Bitmap, Int, CameraSettings, Boolean) -> Unit,
    private val getSettings: () -> CameraSettings,
    private val getIsRawOn: () -> Boolean
) : ImageAnalysis.Analyzer {
    private val scope = CoroutineScope(Dispatchers.Default)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        scope.launch {
            val currentSettings = getSettings()
            val currentIsRawOn = getIsRawOn()
            listener(bitmap, rotationDegrees, currentSettings, currentIsRawOn)
            imageProxy.close()
        }
    }
}
