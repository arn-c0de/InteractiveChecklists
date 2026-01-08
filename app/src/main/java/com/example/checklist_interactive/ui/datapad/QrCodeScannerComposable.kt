package com.example.checklist_interactive.ui.datapad

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat

/**
 * Full-screen QR Code Scanner using ZXing
 * Secure, battle-tested library for QR code scanning
 * CRITICAL: This MUST be shown in a full-screen dialog to get proper camera dimensions
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrCodeScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Wrap in full-screen dialog to escape any parent size constraints
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,  // CRITICAL: allows true fullscreen
            decorFitsSystemWindows = false     // CRITICAL: allows edge-to-edge
        )
    ) {
        QrCodeScannerContent(
            onQrCodeScanned = onQrCodeScanned,
            onDismiss = onDismiss
        )
    }
}

/**
 * The actual full-screen camera content
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrCodeScannerContent(
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasScanned by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            cameraPermissionState.status.isGranted -> {
                // ZXing barcode scanner view with camera preview - MUST BE FIRST IN BOX
                var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

                // Camera preview layer - takes full screen
                AndroidView(
                    factory = { ctx ->
                        DecoratedBarcodeView(ctx).apply {
                            // Configure to only scan QR codes (not barcodes)
                            val formats = listOf(BarcodeFormat.QR_CODE)
                            this.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
                            android.util.Log.d("QrScanner", "📱 QR Scanner initialized - QR_CODE format ONLY")

                            // Ensure view is visible and sized properly - CRITICAL for camera preview
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            
                            // Force view to be visible
                            visibility = android.view.View.VISIBLE
                            android.util.Log.d("QrScanner", "📐 Camera view size: ${layoutParams.width}x${layoutParams.height}")

                            // Set up the callback for scan results
                            decodeContinuous(object : BarcodeCallback {
                                override fun barcodeResult(result: BarcodeResult?) {
                                    if (result != null && !hasScanned && result.barcodeFormat == BarcodeFormat.QR_CODE) {
                                        val scannedText = result.text
                                        android.util.Log.d("QrScanner", "📷 QR Code scanned: ${scannedText.take(50)}...")
                                        
                                        // Accept QR codes with datapad_registration type
                                        if (scannedText.contains("datapad_registration", ignoreCase = true)) {
                                            android.util.Log.i("QrScanner", "✅ Valid DataPad registration QR code detected!")
                                            hasScanned = true
                                            pause()
                                            onQrCodeScanned(scannedText)
                                        } else {
                                            android.util.Log.w("QrScanner", "⚠️ QR code is not a DataPad registration token")
                                        }
                                    } else if (result != null) {
                                        android.util.Log.w("QrScanner", "⚠️ Non-QR barcode detected: ${result.barcodeFormat}")
                                    }
                                }

                                override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {
                                    // Optional: handle possible result points for visual feedback
                                }
                            })

                            scannerView = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),  // Ensure centered alignment
                    update = { view ->
                        // Resume camera when view is updated
                        if (!hasScanned) {
                            view.resume()
                        }
                    }
                )

                // Manage camera lifecycle - start and stop properly
                DisposableEffect(scannerView) {
                    scannerView?.resume()
                    onDispose {
                        // CRITICAL: Stop camera when leaving the scanner
                        scannerView?.pause()
                    }
                }

                // Overlay UI - TOP LAYER - This draws OVER the camera
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top bar with close button
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan QR Code",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Bottom instruction text
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Position QR code within frame",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Using ZXing - secure QR scanner",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            else -> {
                // Permission denied or not yet granted
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "To scan QR codes, please grant camera permission",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    // Cleanup camera when composable is disposed or dismissed
    DisposableEffect(cameraPermissionState.status.isGranted) {
        onDispose {
            // Stop camera to prevent it from running in background
            if (cameraPermissionState.status.isGranted) {
                // Camera will be paused when view is removed
            }
        }
    }
}

