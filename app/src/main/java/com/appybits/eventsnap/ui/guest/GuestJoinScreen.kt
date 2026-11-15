package com.appybits.eventsnap.ui.guest

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.ErrorColor
import com.appybits.eventsnap.ui.theme.GuestTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun GuestJoinScreen(
    onJoinSuccess: (eventId: String, guestId: String) -> Unit,
    onLoginClick: () -> Unit,
    viewModel: GuestJoinViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        if (state is GuestJoinState.Success) {
            val s = state as GuestJoinState.Success
            onJoinSuccess(s.eventId, s.guestId)
            viewModel.resetState()
        }
    }
    GuestJoinContent(
        state = state,
        onJoinByPin = { pin -> viewModel.joinByPin(pin) },
        onJoinByQr = { qrValue -> viewModel.joinByQr(qrValue) },
        onLoginClick = onLoginClick
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GuestJoinContent(
    state: GuestJoinState,
    onJoinByPin: (String) -> Unit,
    onJoinByQr: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isScanningQr by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val keyboardController = LocalSoftwareKeyboardController.current

    GuestTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = "Join event",
                        onBack = if (isScanningQr) { { isScanningQr = false } } else null,
                        actions = {
                            IconButton(onClick = onLoginClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Studio Login", // Important for accessibility
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    )
                },
                containerColor = Color.Transparent
            ) { padding ->
                if (isScanningQr) {
                    BackHandler { isScanningQr = false }
                    if (cameraPermissionState.status.isGranted) {
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            QrScanner(onQrScanned = { qrValue -> isScanningQr = false; onJoinByQr(qrValue) })
                            Button(onClick = { isScanningQr = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) { Text("Cancel Scan") }
                        }
                    } else {
                        LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Camera permission is required to scan QR.", textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { isScanningQr = false }) { Text("Back to PIN") }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Welcome! Let’s find your moments.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan the event QR, enter your PIN, or snap a quick selfie to instantly discover all your photos from the celebration—no account required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        PrimaryButton(
                            text = "Scan QR code",
                            onClick = { isScanningQr = true },
                            enabled = state !is GuestJoinState.Loading
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("or", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        val isError = state is GuestJoinState.Error
                        InputField(
                            value = pin, 
                            onValueChange = { pin = it }, 
                            label = "Enter event PIN", 
                            isError = isError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                                keyboardController?.hide()
                                onJoinByPin(pin) 
                            })
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                        EventSnapOutlinedButton(
                            text = if (isError) "Try another PIN" else "Join",
                            onClick = { onJoinByPin(pin) },
                            enabled = state !is GuestJoinState.Loading
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        // Show what actually went wrong instead of one fixed message for every error.
                        if (state is GuestJoinState.Error) {
                            Text(
                                text = state.message,
                                color = ErrorColor,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Only you can see photos matched to your selfie.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state is GuestJoinState.Loading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuestJoinScreenPreview() {
    GuestJoinContent(state = GuestJoinState.Idle, onJoinByPin = {}, onJoinByQr = {}, onLoginClick = {})
}