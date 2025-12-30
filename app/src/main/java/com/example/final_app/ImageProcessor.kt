package com.example.final_app

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Bitmap.createScaledBitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.core.graphics.scale

object ImageProcessor {

    private lateinit var clahe: CLAHE

    init {
        if (OpenCVLoader.initLocal()) {
            Log.i("ImageProcessor", "OpenCV initialized successfully")
            clahe = Imgproc.createCLAHE(2.0, org.opencv.core.Size(8.0, 8.0))
        } else {
            Log.e("ImageProcessor", "OpenCV initialization failed!")
        }
    }


    fun processImage(originalBitmap: Bitmap, isRawOn: Boolean, settings: CameraSettings): Bitmap {
        Log.d(
            "ImageProcessor",
            "processImage (for capture) called. isRawOn: $isRawOn. Bitmap size: ${originalBitmap.width}x${originalBitmap.height}"
        )
        if (isRawOn) {
            Log.d("ImageProcessor", "Returning original bitmap (raw mode for capture).")
            return originalBitmap
        } else {
            Log.d("ImageProcessor", "Processing image for capture with current settings.")
            return processImageOptimized(originalBitmap, settings)
        }
    }

    fun processImageWithDownScalingAndAdjustments(
        originalBitmap: Bitmap,
        isRawOn: Boolean,
        settings: CameraSettings,
        scaleFactor: Float
    ): Bitmap {
        val scaledWidth = (originalBitmap.width * scaleFactor).roundToInt()
        val scaledHeight = (originalBitmap.height * scaleFactor).roundToInt()

        val scaledBitmap = originalBitmap.scale(scaledWidth, scaledHeight)

        val rgbaMat = Mat()
        Utils.bitmapToMat(scaledBitmap, rgbaMat)

        val outputMat: Mat

        if (isRawOn) {
            outputMat = rgbaMat
            Log.d("ImageProcessor", "Live preview: RAW mode. No adjustments applied.")
        } else {
            Log.d(
                "ImageProcessor",
                "Live preview: PROCESSED mode. Applying paper-inspired ROI pipeline."
            )

            val channels = ArrayList<Mat>(4)
            Core.split(rgbaMat, channels)
            val redChannelMat = channels[0]

            val redChannelWithContext = Mat()
            redChannelMat.copyTo(redChannelWithContext)

            // Define ROI
            val roiWidth = (redChannelMat.cols() * 0.5).roundToInt()
            val roiHeight = (redChannelMat.rows() * 0.45).roundToInt()
            val roiX = (redChannelMat.cols() - roiWidth) / 2
            val roiY = (redChannelMat.rows() - roiHeight) / 2

            val roiRect = Rect(roiX, roiY, roiWidth, roiHeight)
            val roiMatView = Mat(redChannelWithContext, roiRect)

            // --- Start ROI Processing Pipeline (Paper-inspired) ---
            val smoothedRoiMat = Mat()
            val bgCompRoiMat = Mat()
            val gammaCorrectedRoiMat = Mat()
            val gaussianBlurredRoiMat = Mat()
            val claheRoiMat = Mat()
            val binaryVeinMask = Mat()
            val openedMask = Mat()
            val erodedMask = Mat() // NEW: For erosion
            val finalRoiOutput = Mat()

            // 1. Apply Initial Smoothing (Median Blur)
            Imgproc.medianBlur(roiMatView, smoothedRoiMat, 5)

            // 2. Apply Background Compensation (with tunable strength)
            val backgroundCompKernelSize = mapSliderToBackgroundCompKernel(
                settings.backgroundSmoothness,
                redChannelMat.width()
            )
            applyBackgroundCompensation(smoothedRoiMat, bgCompRoiMat, backgroundCompKernelSize)
            Log.d(
                "ImageProcessor",
                "Applied Background Comp Kernel: ${backgroundCompKernelSize}x${backgroundCompKernelSize}"
            )

            // 3. Apply Gamma Correction (Image Brightness)
            val gammaValue = mapSliderToGamma(settings.imageBrightness)
            applyGammaCorrection(bgCompRoiMat, gammaCorrectedRoiMat, gammaValue)
            Log.d("ImageProcessor", "Applied Gamma: ${String.format("%.2f", gammaValue)}")

            // 4. Apply Gaussian Blur for initial noise reduction
            Imgproc.GaussianBlur(
                gammaCorrectedRoiMat,
                gaussianBlurredRoiMat,
                Size(5.0, 5.0),
                0.0,
                0.0
            )

            // 5. CLAHE Enhancement (Vein Clarity)
            val claheClipLimitValue = mapSliderToClaheClipLimit(settings.veinClarity)
            clahe.clipLimit = claheClipLimitValue
            clahe.apply(gaussianBlurredRoiMat, claheRoiMat)
            Log.d(
                "ImageProcessor",
                "Applied CLAHE Clip Limit: ${String.format("%.2f", claheClipLimitValue)}"
            )

            // 6. Adaptive Thresholding (to create a binary mask of veins)
            Imgproc.adaptiveThreshold(
                claheRoiMat, binaryVeinMask, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 21, 5.0
            ) // BlockSize and C can be tuned

            // 7. Morphological Opening on the Mask (Reduction of Noise / Discarding small objects)
            val kernel3x3 = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(3.0, 3.0)
            ) // Small elliptical kernel
            Imgproc.morphologyEx(binaryVeinMask, openedMask, Imgproc.MORPH_OPEN, kernel3x3)

            // 8. NEW: Morphological Erosion on the Opened Mask (to make veins thinner/smoother)
            // Using a 2x2 or 3x3 kernel for a subtle thinning effect.
            val kernel2x2 = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(3.0, 3.0)
            ) // Smaller kernel for erosion
            Imgproc.erode(openedMask, erodedMask, kernel2x2) // Apply erosion

            // 9. Darken Veins in the CLAHE-enhanced ROI using the Eroded Mask
            Core.subtract(claheRoiMat, erodedMask, finalRoiOutput) // Subtracting the eroded mask

            // --- End ROI Processing Pipeline ---

            finalRoiOutput.copyTo(Mat(redChannelWithContext, roiRect))

            outputMat = redChannelWithContext

            // Release all intermediate Mats
            channels.forEach { it.release() }
            redChannelMat.release()
            roiMatView.release()
            smoothedRoiMat.release()
            bgCompRoiMat.release()
            gammaCorrectedRoiMat.release()
            gaussianBlurredRoiMat.release()
            claheRoiMat.release()
            binaryVeinMask.release()
            openedMask.release()
            erodedMask.release() // Release the new erodedMask
            finalRoiOutput.release()
        }

        val finalBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val outputRgbaFinalMat = Mat()
        Imgproc.cvtColor(outputMat, outputRgbaFinalMat, Imgproc.COLOR_GRAY2RGBA)

        Utils.matToBitmap(outputRgbaFinalMat, finalBitmap)

        rgbaMat.release()
        if (!isRawOn) {
            outputMat.release()
            outputRgbaFinalMat.release()
        }
        scaledBitmap.recycle()

        return finalBitmap
    }

    private fun applyGammaCorrection(src: Mat, dst: Mat, gamma: Float) {
        val lut = Mat(1, 256, CvType.CV_8U)
        for (i in 0..255) {
            val exponent = 1.0 / gamma
            val value = (255.0 * (i / 255.0).pow(exponent)).roundToInt()
            lut.put(0, i, byteArrayOf(value.coerceIn(0, 255).toByte()))
        }
        Core.LUT(src, lut, dst)
        lut.release()
    }

    private fun mapSliderToGamma(sliderValue: Float): Float {
        return if (sliderValue <= 50) {
            1.0f + (50f - sliderValue) * 0.02f
        } else {
            1.0f - (sliderValue - 50f) * 0.01f
        }.coerceIn(0.1f, 3.0f)
    }


    private fun mapSliderToBackgroundCompKernel(sliderValue: Float, imageWidth: Int): Int {
        val percentage = 0.01f + (sliderValue / 100f) * 0.19f // Maps 0-100 to 0.01-0.20
        var kernelSize = (imageWidth * percentage).roundToInt()

        if (kernelSize < 3) kernelSize = 3
        if (kernelSize % 2 == 0) kernelSize += 1

        return kernelSize
    }


    private fun mapSliderToClaheClipLimit(sliderValue: Float): Double {
        return (sliderValue * 0.1).coerceIn(0.0, 10.0)
    }


    private fun applyBackgroundCompensation(src: Mat, dst: Mat, kernelSize: Int) {
        val srcFloat = Mat()
        src.convertTo(srcFloat, CvType.CV_32F)

        val blurredBackground = Mat()
        Imgproc.GaussianBlur(
            srcFloat,
            blurredBackground,
            Size(kernelSize.toDouble(), kernelSize.toDouble()),
            0.0,
            0.0
        )

        Core.add(blurredBackground, Scalar(1.0), blurredBackground)

        Core.divide(srcFloat, blurredBackground, dst)

        Core.multiply(dst, Scalar(255.0), dst)

        dst.convertTo(dst, CvType.CV_8U)

        srcFloat.release()
        blurredBackground.release()
    }


    fun processImageOptimized(originalBitmap: Bitmap, settings: CameraSettings): Bitmap {
        val rgbaMat = Mat()
        Utils.bitmapToMat(originalBitmap, rgbaMat)

        val channels = ArrayList<Mat>(4)
        Core.split(rgbaMat, channels)
        val redChannelMat = channels[0]

        val redChannelWithContext = Mat()
        redChannelMat.copyTo(redChannelWithContext)

        // Define ROI
        val roiWidth = (redChannelMat.cols() * 0.5).roundToInt()
        val roiHeight = (redChannelMat.rows() * 0.45).roundToInt()
        val roiX = (redChannelMat.cols() - roiWidth) / 2
        val roiY = (redChannelMat.rows() - roiHeight) / 2

        val roiRect = Rect(roiX, roiY, roiWidth, roiHeight)
        val roiMatView = Mat(redChannelWithContext, roiRect)

        // --- Start ROI Processing Pipeline (Paper-inspired) ---
        val smoothedRoiMat = Mat()
        val bgCompRoiMat = Mat()
        val gammaCorrectedRoiMat = Mat()
        val gaussianBlurredRoiMat = Mat()
        val claheRoiMat = Mat()
        val binaryVeinMask = Mat()
        val openedMask = Mat()
        val erodedMask = Mat()
        val finalRoiOutput = Mat()

        Imgproc.medianBlur(roiMatView, smoothedRoiMat, 5)

        val backgroundCompKernelSize =
            mapSliderToBackgroundCompKernel(settings.backgroundSmoothness, redChannelMat.width())
        applyBackgroundCompensation(smoothedRoiMat, bgCompRoiMat, backgroundCompKernelSize)

        val gammaValue = mapSliderToGamma(settings.imageBrightness)
        applyGammaCorrection(bgCompRoiMat, gammaCorrectedRoiMat, gammaValue)

        Imgproc.GaussianBlur(gammaCorrectedRoiMat, gaussianBlurredRoiMat, Size(5.0, 5.0), 0.0, 0.0)

        val claheClipLimitValue = mapSliderToClaheClipLimit(settings.veinClarity)
        clahe.setClipLimit(claheClipLimitValue)
        clahe.apply(gaussianBlurredRoiMat, claheRoiMat)

        Imgproc.adaptiveThreshold(
            claheRoiMat, binaryVeinMask, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 21, 5.0
        )

        val kernel3x3 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(binaryVeinMask, openedMask, Imgproc.MORPH_OPEN, kernel3x3)

        val kernel2x2 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0, 2.0))
        Imgproc.erode(openedMask, erodedMask, kernel2x2)

        Core.subtract(claheRoiMat, erodedMask, finalRoiOutput)

        // --- End ROI Processing Pipeline ---

        finalRoiOutput.copyTo(Mat(redChannelWithContext, roiRect))

        val processedBitmap = createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        ) // Changed to ARGB_8844 for potential memory optimization or specific display needs
        val outputRgbaFinalMat = Mat()
        Imgproc.cvtColor(redChannelWithContext, outputRgbaFinalMat, Imgproc.COLOR_GRAY2RGBA)

        Utils.matToBitmap(outputRgbaFinalMat, processedBitmap)

        rgbaMat.release()
        channels.forEach { it.release() }
        redChannelMat.release()
        roiMatView.release()
        smoothedRoiMat.release()
        bgCompRoiMat.release()
        gammaCorrectedRoiMat.release()
        gaussianBlurredRoiMat.release()
        claheRoiMat.release()
        binaryVeinMask.release()
        openedMask.release()
        erodedMask.release()
        finalRoiOutput.release()
        redChannelWithContext.release()
        outputRgbaFinalMat.release()

        return processedBitmap
    }

}

