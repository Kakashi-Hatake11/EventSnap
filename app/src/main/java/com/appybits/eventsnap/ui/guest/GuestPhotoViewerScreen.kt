package com.appybits.eventsnap.ui.guest

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.appybits.eventsnap.ui.components.EventSnapBackground
import com.appybits.eventsnap.ui.components.EventSnapTopBar
import com.appybits.eventsnap.ui.theme.EventSnapTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestPhotoViewerScreen(
    photoUrl: String,
    allowDownload: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = "",
                        onBack = onBack,
                        actions = {
                            if (allowDownload) {
                                IconButton(onClick = { downloadPhoto(context, photoUrl) }) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                    )
                },
                containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 3f)
                                val newOffsetX = offsetX + pan.x * scale
                                val newOffsetY = offsetY + pan.y * scale
                                
                                if (scale > 1f) {
                                    offsetX = newOffsetX
                                    offsetY = newOffsetY
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photoUrl)
                            .crossfade(true)
                            .size(coil.size.Size.ORIGINAL) // Load in highest quality for full view
                            .build(),
                        contentDescription = "Full Screen Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            }
        }
    }
}

internal fun downloadPhoto(context: Context, url: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle("Downloading Photo")
        request.setDescription("Downloading high resolution photo from EventSnap")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EventSnap_${System.currentTimeMillis()}.jpg")
        
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Preview
@Composable
fun GuestPhotoViewerScreenPreview() {
    EventSnapTheme {
        GuestPhotoViewerScreen(
            photoUrl = "",
            allowDownload = true,
            onBack = {}
        )
    }
}
