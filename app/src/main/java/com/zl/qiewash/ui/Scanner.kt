package com.zl.qiewash.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onResult)
            Box(
                Modifier.align(Alignment.Center).size(240.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            )
            Text(
                "对准机器上的二维码",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(top = 300.dp),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("需要相机权限才能扫码", color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("授予权限") }
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
        }
    }
}

/**
 * ML Kit instead of a hand-rolled ZXing pass: it handles rotation, blur and
 * inverted codes itself, which is what made the old code miss badly lit labels.
 */
@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(ctx)
        val scanner: BarcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    if (handled.get()) {
                        image.close()
                        return@setAnalyzer
                    }
                    val media = image.image
                    if (media == null) {
                        image.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val text = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                            if (text != null && handled.compareAndSet(false, true)) {
                                view.post { onResult(text) }
                            }
                        }
                        .addOnCompleteListener { image.close() }
                }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
                // Nudge focus once the preview is up; fixed-focus fallback was the old culprit.
                runCatching {
                    camera.cameraControl.startFocusAndMetering(
                        androidx.camera.core.FocusMeteringAction.Builder(
                            view.meteringPointFactory.createPoint(0.5f, 0.5f),
                            androidx.camera.core.FocusMeteringAction.FLAG_AF,
                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                    )
                }
            }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
            runCatching { future.get().unbindAll() }
        }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
