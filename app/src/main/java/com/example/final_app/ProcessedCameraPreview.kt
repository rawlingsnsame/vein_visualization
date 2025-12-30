//package com.example.final_app
//
//import android.graphics.Bitmap
//import android.graphics.Matrix
//import android.util.Log
//import androidx.camera.core.ImageProxy
//import androidx.camera.view.LifecycleCameraController
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.lifecycle.compose.LocalLifecycleOwner
//import java.util.concurrent.Executors
//import androidx.compose.ui.draw.alpha
//import androidx.compose.ui.graphics.Color // Import Color
//import androidx.compose.foundation.background // Import background modifier
//
//@Composable
//fun ProcessedCameraPreview (
//    controller: LifecycleCameraController,
//    getIsRawOn: () -> Boolean,
//    onFrameProcessed: (Bitmap) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    val lifecycleOwner = LocalLifecycleOwner.current
//
//    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
//
//    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
//
//    LaunchedEffect(controller) {
//        Log.d("ProcessedCameraPreview", "LaunchedEffect: Setting up ImageAnalysisAnalyzer.")
//        controller.setImageAnalysisAnalyzer(
//            cameraExecutor,
//            { imageProxy ->
//                val currentRawMode = getIsRawOn()
//                Log.d("ProcessedCameraPreview", "Analyzer: Frame received. currentRawMode: $currentRawMode")
//
//                processFrame(imageProxy, currentRawMode) { bitmap ->
//                    currentFrameBitmap = bitmap
//                    Log.d("ProcessedCameraPreview", "Analyzer: currentFrameBitmap updated.")
//                }
//            }
//        )
//    }
//
//    DisposableEffect(Unit) {
//        onDispose {
//            cameraExecutor.shutdown()
//            Log.d("ProcessedCameraPreview", "DisposableEffect: cameraExecutor shut down.")
//        }
//    }
//
//    val isRawOnDisplay = getIsRawOn()
//    Log.d("ProcessedCameraPreview", "Composing UI. isRawOnDisplay: $isRawOnDisplay")
//
//
//    Box(
//        modifier = modifier
//            .fillMaxSize()
//            .background(Color.Black) // Ensure the background is always black
//    ){
//        // Always include PreviewView in the composition, but control its visibility
//        AndroidView(
//            factory = { ctx ->
//                PreviewView(ctx).apply {
//                    this.controller = controller
//                }
//            },
//            modifier = Modifier
//                .fillMaxSize()
//                .alpha(if (isRawOnDisplay) 1f else 0f) // Show when raw, hide when processed
//        )
//
//        // Always include Image in the composition, but control its visibility
//        currentFrameBitmap?.let { bitmap ->
//            Image(
//                bitmap = bitmap.asImageBitmap(),
//                contentDescription = "Processed Camera Feed",
//                modifier = Modifier
//                    .fillMaxSize()
//                    .alpha(if (isRawOnDisplay) 0f else 1f), // Hide when raw, show when processed
//                contentScale = ContentScale.Crop // Revert to Crop for consistent filling
//            )
//        }
//    }
//}
//
//private fun processFrame(
//    imageProxy: ImageProxy,
//    isRawOn: Boolean,
//    onFrameProcessed: (Bitmap) -> Unit
//){
//    try {
//        Log.d("ProcessFrame", "Processing frame. ImageProxy rotation: ${imageProxy.imageInfo.rotationDegrees} degrees. isRawOn: $isRawOn")
//
//        val bitmap = imageProxy.toBitmap()
//        Log.d("ProcessFrame", "Original bitmap dimensions: ${bitmap.width}x${bitmap.height}")
//
//        val matrix = Matrix().apply{
//            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
//        }
//        val rotatedBitmap = Bitmap.createBitmap(
//            bitmap,
//            0,
//            0,
//            bitmap.width,
//            bitmap.height,
//            matrix,
//            true
//        )
//        Log.d("ProcessFrame", "Rotated bitmap dimensions: ${rotatedBitmap.width}x${rotatedBitmap.height}")
//
//
//        val finalBitmap = if (isRawOn) {
//            Log.d("ProcessFrame", "Returning rotated raw bitmap.")
//            rotatedBitmap
//        }else{
//            Log.d("ProcessFrame", "Calling ImageProcessor.processImageWithDownScaling.")
//            // Pass isRawOn = false to ImageProcessor to ensure processing
//            ImageProcessor.processImageWithDownScaling(rotatedBitmap, isRawOn = false, scaleFactor = 0.5f)
//        }
//
//        onFrameProcessed(finalBitmap)
//        Log.d("ProcessFrame", "Frame processed and callback invoked.")
//    }catch (e: Exception){
//        Log.e("ProcessFrame", "Error processing frame: ${e.message}", e)
//    }finally {
//        imageProxy.close()
//        Log.d("ProcessFrame", "ImageProxy closed.")
//    }
//}

package com.example.final_app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun ProcessedCameraPreview (
    controller: LifecycleCameraController,
    getIsRawOn: () -> Boolean,
    getSettings: () -> CameraSettings, // Lambda to get current CameraSettings
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(controller) {
        Log.d("ProcessedCameraPreview", "LaunchedEffect: Setting up ImageAnalysisAnalyzer.")
        controller.setImageAnalysisAnalyzer(
            cameraExecutor,
            { imageProxy ->
                val currentRawMode = getIsRawOn()
                val currentSettings = getSettings() // Get current settings here
                Log.d("ProcessedCameraPreview", "Analyzer: Frame received. currentRawMode: $currentRawMode, settings: $currentSettings")

                processFrame(imageProxy, currentRawMode, currentSettings) { bitmap -> // Pass settings
                    currentFrameBitmap = bitmap
                    Log.d("ProcessedCameraPreview", "Analyzer: currentFrameBitmap updated.")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            Log.d("ProcessedCameraPreview", "DisposableEffect: cameraExecutor shut down.")
        }
    }

    val isRawOnDisplay = getIsRawOn()
    Log.d("ProcessedCameraPreview", "Composing UI. isRawOnDisplay: $isRawOnDisplay")


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Ensure the background is always black
    ){
        // Always include PreviewView in the composition, but control its visibility
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isRawOnDisplay) 1f else 0f) // Show when raw, hide when processed
        )

        // Always include Image in the composition, but control its visibility
        currentFrameBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Processed Camera Feed",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isRawOnDisplay) 0f else 1f), // Hide when raw, show when processed
                contentScale = ContentScale.Crop // Revert to Crop for consistent filling
            )
        }
    }
}

private fun processFrame(
    imageProxy: ImageProxy,
    isRawOn: Boolean,
    settings: CameraSettings, // New parameter for settings
    onFrameProcessed: (Bitmap) -> Unit
){
    try {
        Log.d("ProcessFrame", "Processing frame. ImageProxy rotation: ${imageProxy.imageInfo.rotationDegrees} degrees. isRawOn: $isRawOn. Settings: $settings")

        val originalBitmap = imageProxy.toBitmap()
        Log.d("ProcessFrame", "Original bitmap dimensions: ${originalBitmap.width}x${originalBitmap.height}")

        val matrix = Matrix().apply{
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            matrix,
            true
        )
        Log.d("ProcessFrame", "Rotated bitmap dimensions: ${rotatedBitmap.width}x${rotatedBitmap.height}")

        val finalBitmap = ImageProcessor.processImageWithDownScalingAndAdjustments(
            rotatedBitmap,
            isRawOn,
            settings, // Pass the settings here
            0.5f // Use a scale factor for performance
        )

        onFrameProcessed(finalBitmap)
        Log.d("ProcessFrame", "Frame processed and callback invoked.")
    }catch (e: Exception){
        Log.e("ProcessFrame", "Error processing frame: ${e.message}", e)
    }finally {
        imageProxy.close()
        Log.d("ProcessFrame", "ImageProxy closed.")
    }
}
