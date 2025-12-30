# Vein Visualization App
An Android application that uses real-time computer vision to enhance the visibility of veins. This project leverages OpenCV to process camera frames through a custom pipeline designed to isolate and contrast venous patterns from the skin surface.
 ## Features
 - Live Preview Enhancement: Real-time processing of camera frames with down-scaling for performance. 
 - ROI-Based Processing: Focuses heavy computation on a specific Region of Interest (ROI) to ensure high frame rates.
 - Paper-Inspired Pipeline: Implements a scientific approach to vein extraction, including:
 - Background Compensation: Normalizes lighting across the skin surface.
 - CLAHE: Contrast Limited Adaptive Histogram Equalization for vein clarity.
 - Adaptive Thresholding: Dynamically generates binary masks of venous structures.
 - Morphological Operations: Opening and Erosion to reduce noise and refine vein edges.
 - Customizable Settings: Real-time adjustments for:
   - Background Smoothness
   - Image Brightness (Gamma Correction)
   - Vein Clarity (CLAHE Clip Limit)
   - High-Res Capture: Optimized separate pipeline for capturing high-quality processed images.
## Tech Stack
- Kotlin: Primary programming language.
- Jetpack Compose: Modern UI toolkit for the camera interface and controls.
- OpenCV (4.12.0): Used for all image processing and matrix manipulations.
- CameraX: For robust Android camera hardware abstraction.
