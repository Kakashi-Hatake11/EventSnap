package com.appybits.eventsnap.ui.admin

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import com.google.zxing.BarcodeFormat
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import com.google.zxing.qrcode.QRCodeWriter
import com.appybits.eventsnap.ui.components.EventSnapBackground
import com.appybits.eventsnap.ui.components.EventSnapTopBar

@Composable
fun EventQRCodeScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    val event by viewModel.event.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    EventQRCodeContent(event = event, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventQRCodeContent(
    event: Event?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(title = "Event QR Code", onBack = onBack)
                },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    event?.let { ev ->
                        if (ev.qrCodeValue.isNotEmpty()) {
                            val bitmap = generateQRCode(ev.qrCodeValue)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Event QR Code",
                                    modifier = Modifier.size(250.dp)
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(text = "Event PIN: ${ev.pin}", style = MaterialTheme.typography.headlineMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Scan this code to join the event", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                Button(onClick = { shareQrCode(context, bitmap, ev) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share QR Code & PIN")
                                }
                            } else {
                                Text("Error generating QR code")
                            }
                        } else {
                            Text("QR Code not available yet.")
                        }
                    }
                }
            }
        }
    }
}

fun generateQRCode(content: String): Bitmap? {
    try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun shareQrCode(context: android.content.Context, bitmap: Bitmap, event: Event) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs() // don't forget to make the directory
        val stream = FileOutputStream("$cachePath/image.png") // overwrites this image every time
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val imagePath = File(context.cacheDir, "images")
        val newFile = File(imagePath, "image.png")
        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Join my event on EventSnap!\n\nEvent Name: ${event.name}\nPIN: ${event.pin}\n\nOr scan the attached QR Code.")
                type = "image/png"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Event Details"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Preview(showBackground = true)
@Composable
fun EventQRCodeScreenPreview() {
    EventSnapTheme {
        EventQRCodeContent(
            event = Event(
                id = "1",
                name = "Tech Conference 2026",
                qrCodeValue = "sample-qr-code-value",
                pin = "5678"
            ),
            onBack = {}
        )
    }
}
