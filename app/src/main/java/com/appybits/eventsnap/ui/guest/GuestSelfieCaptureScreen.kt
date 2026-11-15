package com.appybits.eventsnap.ui.guest

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect

import androidx.compose.ui.graphics.drawscope.clipPath

import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.GuestTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executors

@Composable
fun GuestSelfieCaptureScreen(
    eventId: String,
    guestId: String,
    onUploadSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: GuestSelfieViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    GuestSelfieContent(
        state = state,
        onUploadSelfie = { uri -> viewModel.uploadSelfie(eventId, guestId, uri) },
        onResetState = { viewModel.resetState() },
        onUploadSuccess = {
            com.appybits.eventsnap.utils.NotificationHelper.showSelfieMatchedNotification(context)
            onUploadSuccess()
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GuestSelfieContent(
    state: GuestSelfieState,
    onUploadSelfie: (Uri) -> Unit,
    onResetState: () -> Unit,
    onUploadSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    GuestTheme {
        EventSnapBackground {
            Scaffold(
                topBar = { EventSnapTopBar(title = "Find your photos", onBack = onBack) },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                if (cameraPermissionState.status.isGranted) {
                    if (capturedImageUri == null) {
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            AndroidView(
                                factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = this } },
                                modifier = Modifier.fillMaxSize()
                            )
                            LaunchedEffect(previewView, lensFacing) {
                                val pv = previewView ?: return@LaunchedEffect
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = CameraPreview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                                    imageCapture = ImageCapture.Builder().build()
                                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                                    } catch (e: Exception) { Log.e("SelfieCapture", "Camera bind fail", e) }
                                }, ContextCompat.getMainExecutor(context))
                            }
                            
                            // Human Round Face Guide Overlay
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val radius = canvasWidth * 0.35f
                                val center = androidx.compose.ui.geometry.Offset(canvasWidth / 2, canvasHeight / 2 - 50.dp.toPx())

                                val path = androidx.compose.ui.graphics.Path().apply {
                                    addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                                }

                                clipPath(path, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                    drawRect(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                                }
                                
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    radius = radius,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                                    )
                                )
                            }

                            // Flip Camera Button
                            IconButton(
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                        CameraSelector.LENS_FACING_BACK
                                    } else {
                                        CameraSelector.LENS_FACING_FRONT
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Cameraswitch, contentDescription = "Flip Camera", tint = androidx.compose.ui.graphics.Color.White)
                            }
                            
                            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                                PrimaryButton(
                                    text = "Take selfie",
                                    onClick = {
                                        val ic = imageCapture ?: return@PrimaryButton
                                        val photoFile = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                                        ic.takePicture(
                                            ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) { capturedImageUri = Uri.fromFile(photoFile) }
                                                override fun onError(exc: ImageCaptureException) {}
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(painter = rememberAsyncImagePainter(capturedImageUri), contentDescription = null, modifier = Modifier.weight(1f).fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            if (state is GuestSelfieState.Success) {
                                LaunchedEffect(Unit) {
                                    onUploadSuccess()
                                    onResetState()
                                }
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Logging in...", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                PrimaryButton(text = "Upload", onClick = { capturedImageUri?.let { onUploadSelfie(it) } }, isLoading = state is GuestSelfieState.Uploading)
                                Spacer(modifier = Modifier.height(9.dp))
                                EventSnapOutlinedButton(text = "Retake", onClick = { capturedImageUri = null })
                            }
                        }
                    }
                } else {
                    LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Camera permission required.") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuestSelfieCaptureScreenPreview() { GuestSelfieContent(state = GuestSelfieState.Idle, onUploadSelfie = {}, onResetState = {}, onUploadSuccess = {}, onBack = {}) }
