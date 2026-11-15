package com.appybits.eventsnap.ui.admin

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import android.content.Intent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.model.Photographer
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import com.appybits.eventsnap.ui.admin.generateQRCode

@Composable
fun ManagePhotographersScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    val photographers by viewModel.photographers.collectAsState()
    LaunchedEffect(eventId) { viewModel.loadEvent(eventId) }

    ManagePhotographersContent(
        photographers = photographers,
        onAddPhotographer = { username, onSuccess -> viewModel.addPhotographer(username, onSuccess) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePhotographersContent(
    photographers: List<Photographer>,
    onAddPhotographer: (String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var newUsername by remember { mutableStateOf("") }
    val context = LocalContext.current
    var selectedPhotographer by remember { mutableStateOf<Photographer?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    EventSnapTheme {
        EventSnapBackground {
            if (selectedPhotographer != null) {
                val p = selectedPhotographer!!
                AlertDialog(
                    onDismissRequest = { selectedPhotographer = null },
                    title = { Text("Photographer Login") },
                    text = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            val qrContent = "PHOTOGRAPHER|${p.eventId}|${p.username}|${p.pin}"
                            val bitmap = generateQRCode(qrContent)
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Photographer QR Code",
                                    modifier = Modifier.size(200.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Text("Event ID: ${p.eventId}")
                            Text("Username: ${p.username}")
                            Text("PIN: ${p.pin}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedPhotographer = null
                        }) {
                            Text("Close")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            val qrContent = "PHOTOGRAPHER|${p.eventId}|${p.username}|${p.pin}"
                            val bitmap = generateQRCode(qrContent)
                            if (bitmap != null) {
                                try {
                                    val cachePath = File(context.cacheDir, "images")
                                    cachePath.mkdirs()
                                    val stream = FileOutputStream("$cachePath/image.png")
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    stream.close()

                                    val imagePath = File(context.cacheDir, "images")
                                    val newFile = File(imagePath, "image.png")
                                    val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)

                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        putExtra(Intent.EXTRA_TEXT, "Event Photographer Credentials:\n\nEvent ID: ${p.eventId}\nUsername: ${p.username}\nPIN: ${p.pin}\n\nOr scan the attached QR Code.")
                                        type = "image/png"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Credentials"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Text("Share Info")
                        }
                    }
                )
            }

            Scaffold(
                topBar = { EventSnapTopBar(title = "Photographers", onBack = onBack) },
                bottomBar = {
                    Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                        PrimaryButton(
                            text = "Add photographer",
                            onClick = {
                                if (newUsername.isNotBlank()) {
                                    onAddPhotographer(newUsername) { newUsername = "" }
                                }
                            },
                            enabled = newUsername.isNotBlank()
                        )
                    }
                },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    InputField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = "New Username",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                            keyboardController?.hide()
                            if (newUsername.isNotBlank()) {
                                onAddPhotographer(newUsername) { newUsername = "" }
                            }
                        })
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(photographers) { p ->
                            ListTile(
                                title = p.username,
                                subtitle = "PIN ${p.pin}",
                                statusText = "Active",
                                onClick = {
                                    selectedPhotographer = p
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManagePhotographersScreenPreview() {
    ManagePhotographersContent(
        photographers = listOf(
            Photographer(username = "rohan_k", pin = "1234"),
            Photographer(username = "meera_s", pin = "5678")
        ),
        onAddPhotographer = { _, _ -> },
        onBack = {}
    )
}
