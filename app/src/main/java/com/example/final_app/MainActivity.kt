package com.example.final_app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Range
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api as ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RawOff
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.final_app.ui.theme.Final_appTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) {
            Log.e("MainActivity", "OpenCV initialization failed.")
        } else {
            Log.d("MainActivity", "OpenCV initialized successfully from MainActivity.")
        }

        if (!hasRequiredPermissions()) {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        setContent {
            Final_appTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel = viewModel<MainViewModel>()
                    val bitmaps by viewModel.bitmaps.collectAsState()

                    CameraScreen(
                        bitmaps = bitmaps,
                        onPhotoTaken = viewModel::onTakePhoto
                    )
                }
            }
        }
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        REQUIRED_PERMISSIONS.forEach {
            if (ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED) {
                permissionGranted = false
            }
        }
        if (!permissionGranted) {
            Log.e("MainActivity", "Permissions not granted by the user.")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CameraScreen(
    bitmaps: List<Bitmap>,
    onPhotoTaken: (Bitmap, Boolean) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    var isRawOn by remember { mutableStateOf(true) }
    var isSettingsPanelVisible by remember { mutableStateOf(false) }

    var cameraSettings by remember { mutableStateOf(CameraSettings()) }

    LaunchedEffect(lifecycleOwner, controller) {
        controller.bindToLifecycle(lifecycleOwner)
        Log.d("MainActivity", "CameraController bound to lifecycle.")
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            PhotoBottomContent(bitmaps = bitmaps)
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ProcessedCameraPreview(
                controller = controller,
                getIsRawOn = { isRawOn },
                getSettings = { cameraSettings },
            )

            CameraControls(
                onTakePhoto = {
                    scope.launch {
                        try {
                            val originalBitmap = takePhoto(controller, context)

                            val finalBitmapToSave = if (isRawOn) {
                                originalBitmap
                            } else {
                                withContext(Dispatchers.Default) {
                                    ImageProcessor.processImage(originalBitmap, isRawOn, cameraSettings)
                                }
                            }

                            onPhotoTaken(finalBitmapToSave, isRawOn)
                            scaffoldState.bottomSheetState.expand()
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Error during photo capture or processing: ${e.message}", e)
                        }
                    }
                },
                onToggleCamera = {
                    controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                },
                onToggleRaw = {
                    isRawOn = !isRawOn
                    if (isRawOn) {
                        isSettingsPanelVisible = false
                    }
                    Log.d("CameraScreen", "Toggle RAW: isRawOn is now $isRawOn")
                },
                isRawOn = isRawOn,
                onSettingsClick = {
                    if (!isRawOn) {
                        isSettingsPanelVisible = !isSettingsPanelVisible
                    }
                },
                isSettingsEnabled = !isRawOn,
                onGalleryClick = {
                    scope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                }
            )
        }

        if (isSettingsPanelVisible) {
            SettingsPanel(
                currentSettings = cameraSettings,
                onSettingsChange = { newSettings ->
                    cameraSettings = newSettings
                    Log.d("CameraScreen", "Settings changed to: $newSettings")
                },
                onClose = {
                    isSettingsPanelVisible = false
                }
            )
        }
    }
}

private suspend fun takePhoto(
    controller: LifecycleCameraController,
    context: android.content.Context
): Bitmap = suspendCancellableCoroutine { continuation ->
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                Log.d("MainActivity", "onCaptureSuccess: Image captured.")
                val rotationDegrees = image.imageInfo.rotationDegrees
                val bitmap = image.toBitmap()

                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
                image.close()
                continuation.resume(rotatedBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("MainActivity", "Error capturing image: ${exception.message}", exception)
                continuation.resumeWithException(exception)
            }
        }
    )
}

// CameraSettings data class simplified with user-friendly names
data class CameraSettings(
    val veinClarity: Float = 50f,          // Maps to CLAHE clip limit
    val imageBrightness: Float = 50f,      // Maps to Gamma
    val backgroundSmoothness: Float = 50f  // Maps to Background Comp. Strength
)

@Composable
fun CustomSeekBar(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    valueSuffix: String = ""
){
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ){
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ){
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${value.toInt()}$valueSuffix",
                color = Color(0xFF4682A9),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4682A9),
                activeTrackColor = Color(0xFF4682A9),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsPanel (
    isVisible: Boolean = true,
    currentSettings: CameraSettings,
    onSettingsChange: (CameraSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
){
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            animationSpec = tween(300),
            initialScale = 0.8f
        ),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
            animationSpec = tween(200),
            targetScale = 0.8f
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha=0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ){
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Bottom) // Allows height to adjust to content
                    .padding(bottom = 16.dp)
                    .clickable(false) { /* Consume clicks on the card to prevent dismissing */ },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 12.dp
                )
            ){
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ){
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Image Settings",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Vein Clarity Control (formerly CLAHE Clip Limit)
                    CustomSeekBar(
                        label = "Vein Clarity",
                        value = currentSettings.veinClarity,
                        onValueChange = { newValue ->
                            onSettingsChange(currentSettings.copy(veinClarity = newValue))
                        },
                        valueSuffix = "%" // Can represent intensity or percentage of clarity
                    )

                    // Image Brightness Control (formerly Gamma)
                    CustomSeekBar(
                        label = "Image Brightness",
                        value = currentSettings.imageBrightness,
                        onValueChange = { newValue ->
                            onSettingsChange(currentSettings.copy(imageBrightness = newValue))
                        },
                        valueSuffix = "%" // Can represent percentage of brightness
                    )

                    // Background Smoothness Control (formerly Background Comp. Strength)
                    CustomSeekBar(
                        label = "Background Smoothness",
                        value = currentSettings.backgroundSmoothness,
                        onValueChange = { newValue ->
                            onSettingsChange(currentSettings.copy(backgroundSmoothness = newValue))
                        },
                        valueSuffix = "%" // Can represent percentage of smoothing
                    )

                    // Reset Button
                    Button(
                        onClick = {
                            onSettingsChange(CameraSettings()) // Reset all three settings to defaults
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4682A9).copy(alpha = 0.2f),
                            contentColor = Color(0xFF4682A9)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Reset to Defaults",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraControls(
    onTakePhoto: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleRaw: () -> Unit,
    isRawOn: Boolean,
    onSettingsClick: () -> Unit,
    isSettingsEnabled: Boolean,
    onGalleryClick: () -> Unit
) {
    val IconColor = Color(0xFFFEFAE0)
    val AccentColor = Color(0xFF4682A9)
    val RawIconSize = 60.dp
    val DefaultIconSize = 70.dp

    @Composable
    fun CustomIconButton(
        imageVector: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        iconSize: Dp,
        iconColor: Color = IconColor,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed && enabled) 1.2f else 1f,
            label = "iconScaleAnimation"
        )

        IconButton(
            onClick = onClick,
            modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
            interactionSource = interactionSource,
            enabled = enabled
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) iconColor else iconColor.copy(alpha = 0.4f)
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = if (isRawOn) "RAW MODE" else "PROCESSED MODE",
            color = Color(0xFFFEFAE0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .offset(220.dp, 32.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        )

        CustomIconButton(
            imageVector = if (isRawOn) Icons.Default.RawOn else Icons.Default.RawOff,
            contentDescription = if (isRawOn) "Raw mode is ON" else "Raw mode is OFF",
            onClick = onToggleRaw,
            iconSize = RawIconSize,
            iconColor = if (isRawOn) AccentColor else IconColor,
            modifier = Modifier.offset(20.dp, 30.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .offset(y = (-50).dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomIconButton(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Open Gallery",
                onClick = onGalleryClick,
                iconSize = DefaultIconSize
            )

            CustomIconButton(
                imageVector = Icons.Default.Camera,
                contentDescription = "Take Photo",
                onClick = onTakePhoto,
                iconSize = DefaultIconSize
            )

            CustomIconButton(
                imageVector = Icons.Default.Settings,
                contentDescription = "Image Settings",
                onClick = onSettingsClick,
                iconSize = DefaultIconSize,
                iconColor = if (isSettingsEnabled) AccentColor else IconColor,
                enabled = isSettingsEnabled
            )
        }
    }
}
