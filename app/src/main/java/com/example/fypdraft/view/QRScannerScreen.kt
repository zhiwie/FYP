package com.example.fypdraft.view

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage

// ─────────────────────────────────────────────────────────────────────────────
// QrScannerScreen
//
// Usage: call this composable when you want to scan a MoodSync QR code.
// It will call onScannedUid(uid) once a valid moodsync://add-friend/{uid}
// QR code is detected.
//
// Required dependencies in build.gradle (app):
//   implementation "com.google.mlkit:barcode-scanning:17.2.0"
//   implementation "androidx.camera:camera-camera2:1.3.1"
//   implementation "androidx.camera:camera-lifecycle:1.3.1"
//   implementation "androidx.camera:camera-view:1.3.1"
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(
    onScannedUid: (String) -> Unit,
    onBack: () -> Unit
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope         = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanComplete        by remember { mutableStateOf(false) }
    var errorMessage        by remember { mutableStateOf<String?>(null) }

    // Request camera permission
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        val current = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (current == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        if (!hasCameraPermission) {
            // ── Permission denied state ──────────────────────────────
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera permission needed\nto scan QR codes",
                        color     = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize  = 16.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) { Text("Grant Permission") }
                }
            }
        } else if (!scanComplete) {
            // ── Camera preview ───────────────────────────────────────
            val previewView = remember { PreviewView(context) }
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(Unit) {
                onDispose { cameraExecutor.shutdown() }
            }

            AndroidView(
                factory  = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(hasCameraPermission) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, scanComplete) { uid ->
                            if (!scanComplete) {
                                scanComplete = true
                                scope.launch { onScannedUid(uid) }
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Camera bind failed", e)
                        errorMessage = "Could not open camera: ${e.message}"
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            // ── Scanning overlay UI ──────────────────────────────────
            ScannerOverlay()

        } else {
            // ── Scan success feedback ────────────────────────────────
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 52.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("QR Code Scanned!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Adding friend…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            }
        }

        // ── Error overlay ────────────────────────────────────────────
        if (errorMessage != null) {
            Box(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A2E).copy(alpha = 0.9f))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage ?: "", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // ── Back button ──────────────────────────────────────────────
        IconButton(
            onClick  = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
        }

        // ── Label ────────────────────────────────────────────────────
        if (!scanComplete) {
            Text(
                "Scan a MoodSync Code",
                color    = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scanner overlay — animated corner brackets + scan line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "scan_y"
    )

    Box(Modifier.fillMaxSize()) {
        // Dark overlay around the scan window
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        // Transparent cutout — the scan box (260×260 centred)
        Box(
            modifier           = Modifier.size(260.dp).align(Alignment.Center),
            contentAlignment   = Alignment.Center
        ) {
            // Clear box (transparent)
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
                    .border(2.dp, Color(0xFF1DB954), RoundedCornerShape(16.dp))
            )

            // Animated scan line
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .offset(y = ((-120f + 240f * scanLineY)).dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF1DB954), Color.Transparent)
                        )
                    )
            )

            // Corner brackets — top left
            CornerBracket(Modifier.align(Alignment.TopStart), topLeft = true)
            CornerBracket(Modifier.align(Alignment.TopEnd), topRight = true)
            CornerBracket(Modifier.align(Alignment.BottomStart), bottomLeft = true)
            CornerBracket(Modifier.align(Alignment.BottomEnd), bottomRight = true)
        }

        // Hint text below the box
        Text(
            "Point at a friend's Music Code",
            color     = Color.White.copy(alpha = 0.8f),
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .align(Alignment.Center)
                .padding(top = 300.dp)
        )
    }
}

@Composable
private fun CornerBracket(
    modifier: Modifier,
    topLeft: Boolean = false,
    topRight: Boolean = false,
    bottomLeft: Boolean = false,
    bottomRight: Boolean = false
) {
    val color     = Color(0xFF1DB954)
    val armLen    = 22.dp
    val thickness = 3.dp

    Box(modifier.size(armLen)) {
        // Horizontal arm
        Box(
            Modifier
                .width(armLen * 0.65f)
                .height(thickness)
                .background(color)
                .align(
                    when {
                        topLeft     -> Alignment.TopStart
                        topRight    -> Alignment.TopEnd
                        bottomLeft  -> Alignment.BottomStart
                        else        -> Alignment.BottomEnd
                    }
                )
        )
        // Vertical arm
        Box(
            Modifier
                .width(thickness)
                .height(armLen * 0.65f)
                .background(color)
                .align(
                    when {
                        topLeft     -> Alignment.TopStart
                        topRight    -> Alignment.TopEnd
                        bottomLeft  -> Alignment.BottomStart
                        else        -> Alignment.BottomEnd
                    }
                )
        )
    }
}

// ── Parses "moodsync://add-friend/{uid}" → uid ────────────────────────────

fun extractUidFromQr(raw: String): String? {
    val prefix = "moodsync://add-friend/"
    return if (raw.startsWith(prefix)) {
        val uid = raw.removePrefix(prefix).trim()
        if (uid.isNotEmpty()) uid else null
    } else null
}

// ── Image analysis — @OptIn must be on the function declaration, not in a lambda

@androidx.annotation.OptIn(ExperimentalGetImage::class)
//@OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: androidx.camera.core.ImageProxy,
    scanComplete: Boolean,
    onUidFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || scanComplete) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                // Try both rawValue and displayValue — custom schemes like moodsync://
                // can come back as TYPE_TEXT, TYPE_URL, or other types depending
                // on the ML Kit version. Accept all types and try both value fields.
                val candidates = listOfNotNull(barcode.rawValue, barcode.displayValue)
                Log.d("QrScanner", "Barcode type=${barcode.valueType} candidates=$candidates")
                for (raw in candidates) {
                    val uid = extractUidFromQr(raw)
                    if (uid != null) {
                        Log.d("QrScanner", "✅ Parsed UID: $uid")
                        onUidFound(uid)
                        return@addOnSuccessListener
                    }
                }
            }
            if (barcodes.isEmpty()) Log.d("QrScanner", "No barcodes detected in frame")
        }
        .addOnFailureListener { Log.e("QrScanner", "Barcode scan failed", it) }
        .addOnCompleteListener { imageProxy.close() }
}