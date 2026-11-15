package com.appybits.eventsnap.ui.admin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.repository.StudioRequest
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.guest.QrScanner
import com.appybits.eventsnap.ui.photographer.LoginState
import com.appybits.eventsnap.ui.photographer.PhotographerLoginViewModel
import com.appybits.eventsnap.ui.theme.ErrorColor
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// TODO: put your real support address here.
private const val SUPPORT_EMAIL = "support@yourdomain.com"

@Composable
fun UnifiedLoginScreen(
    onAdminLoginSuccess: () -> Unit,
    onPhotographerLoginSuccess: (eventId: String, photographerId: String) -> Unit,
    onBack: () -> Unit,
    adminViewModel: AdminAuthViewModel = hiltViewModel(),
    photographerViewModel: PhotographerLoginViewModel = hiltViewModel()
) {
    val adminState by adminViewModel.uiState.collectAsState()
    val photographerState by photographerViewModel.uiState.collectAsState()

    LaunchedEffect(adminState) {
        if (adminState is AuthState.Success) {
            onAdminLoginSuccess()
            adminViewModel.resetState()
        }
    }

    LaunchedEffect(photographerState) {
        val state = photographerState
        if (state is LoginState.Success) {
            onPhotographerLoginSuccess(state.eventId, state.photographerId)
            photographerViewModel.resetState()
        }
    }

    UnifiedLoginContent(
        adminState = adminState,
        photographerState = photographerState,
        onAdminLogin = adminViewModel::login,
        onAdminCompleteFirstLogin = adminViewModel::completeFirstLogin,
        onAdminRequestAccess = adminViewModel::submitRequest,
        onAdminClearState = adminViewModel::resetState,
        onPhotographerLogin = photographerViewModel::login,
        onBack = onBack
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UnifiedLoginContent(
    adminState: AuthState,
    photographerState: LoginState,
    onAdminLogin: (username: String, password: String) -> Unit,
    onAdminCompleteFirstLogin: (username: String, tempPassword: String, newPassword: String) -> Unit,
    onAdminRequestAccess: (StudioRequest) -> Unit,
    onAdminClearState: () -> Unit,
    onPhotographerLogin: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) } // 0: Studio, 1: Photographer
    var isScanningQr by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(title = "Studio / Photographer Login", onBack = if (isScanningQr) { { isScanningQr = false } } else onBack)
                },
                containerColor = Color.Transparent
            ) { padding ->
                if (isScanningQr) {
                    BackHandler { isScanningQr = false }
                    if (cameraPermissionState.status.isGranted) {
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            QrScanner(onQrScanned = { qrValue ->
                                isScanningQr = false
                                val parts = qrValue.split("|")
                                if (parts.size == 4 && parts[0] == "PHOTOGRAPHER") {
                                    onPhotographerLogin(parts[1], parts[2], parts[3])
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Invalid QR code. Please scan a photographer QR code.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            })
                            Button(
                                onClick = { isScanningQr = false },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                            ) {
                                Text("Cancel scan")
                            }
                        }
                    } else {
                        LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Camera permission is required to scan a QR code.",
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { isScanningQr = false }) { Text("Back") }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = {
                                    selectedTabIndex = 0
                                    onAdminClearState()
                                },
                                text = { Text("Studio", style = MaterialTheme.typography.titleMedium) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = {
                                    selectedTabIndex = 1
                                    onAdminClearState()
                                },
                                text = { Text("Photographer", style = MaterialTheme.typography.titleMedium) }
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            if (selectedTabIndex == 0) {
                                StudioLoginTab(
                                    state = adminState,
                                    onLogin = onAdminLogin,
                                    onCompleteFirstLogin = onAdminCompleteFirstLogin,
                                    onRequestAccess = onAdminRequestAccess,
                                    onClearState = onAdminClearState
                                )
                            } else {
                                PhotographerLoginTab(
                                    state = photographerState,
                                    onLogin = onPhotographerLogin,
                                    onScanQr = { isScanningQr = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioLoginTab(
    state: AuthState,
    onLogin: (username: String, password: String) -> Unit,
    onCompleteFirstLogin: (username: String, tempPassword: String, newPassword: String) -> Unit,
    onRequestAccess: (StudioRequest) -> Unit,
    onClearState: () -> Unit
) {
    val context = LocalContext.current

    var isRequestMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    // Plain remember on purpose: the password is never written to saved state.
    var password by remember { mutableStateOf("") }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var showForgotInfo by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isLoading = state is AuthState.Loading

    LaunchedEffect(state) {
        if (state is AuthState.PasswordChangeRequired) showChangePassword = true
    }

    // First login with a temporary password: must choose a new one before entering.
    if (showChangePassword) {
        ChangePasswordDialog(
            isLoading = isLoading,
            errorMessage = (state as? AuthState.Error)?.message,
            message = "Your account was set up with a temporary password. " +
                    "Choose your own password to continue.",
            currentLabel = "Temporary password",
            onSubmit = { current, new -> onCompleteFirstLogin(username.trim(), current, new) },
            onDismiss = {
                showChangePassword = false
                password = ""
                onClearState()
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isRequestMode) {
            if (state is AuthState.RequestSubmitted) {
                Text("Request received", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Thank you! Our team will contact you on the phone number or email " +
                            "you provided to explain the next steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Back to sign in",
                    onClick = {
                        isRequestMode = false
                        onClearState()
                    },
                    enabled = true,
                    isLoading = false
                )
            } else {
                Text("Request an account", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tell us about your studio and our team will get in touch.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                StudioRequestForm(isLoading = isLoading, onSubmit = onRequestAccess)

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        isRequestMode = false
                        onClearState()
                    },
                    enabled = !isLoading
                ) {
                    Text(
                        "Already have an account? Sign in",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state is AuthState.Error) StatusMessage(state.message)
            }
        } else {
            val usernameError =
                if (username.isNotBlank()) usernameFormatError(username) else null
            val canSubmit = !isLoading && username.isNotBlank() && password.isNotEmpty() &&
                    usernameFormatError(username) == null

            Text("Studio login", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            InputField(
                value = username, 
                onValueChange = { username = it }, 
                label = "Username",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            FieldError(usernameError)
            Spacer(modifier = Modifier.height(9.dp))

            InputField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { 
                    keyboardController?.hide()
                    if (canSubmit) onLogin(username.trim(), password) 
                })
            )
            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Sign in",
                onClick = { onLogin(username.trim(), password) },
                enabled = canSubmit,
                isLoading = isLoading
            )

            TextButton(onClick = { showForgotInfo = !showForgotInfo }) {
                Text("Forgot password?", color = MaterialTheme.colorScheme.primary)
            }
            if (showForgotInfo) {
                Text(
                    "Password resets are handled by our team. Email support and we'll " +
                            "help you get back in.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { emailSupport(context) }) {
                    Text("Email support", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    isRequestMode = true
                    password = ""
                    showForgotInfo = false
                    onClearState()
                },
                enabled = !isLoading
            ) {
                Text(
                    "New studio? Request an account",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // While the change-password dialog is open, errors show inside it instead.
            if (state is AuthState.Error && !showChangePassword) StatusMessage(state.message)
        }
    }
}

private fun emailSupport(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Password reset request")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Email us at $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun StatusMessage(message: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = message,
        color = ErrorColor,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun FieldError(message: String?) {
    if (message != null) {
        Text(
            text = message,
            color = ErrorColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)
        )
    }
}

@Composable
fun PhotographerLoginTab(
    state: LoginState,
    onLogin: (String, String, String) -> Unit,
    onScanQr: () -> Unit
) {
    var eventId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Photographer login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        InputField(value = eventId, onValueChange = { eventId = it }, label = "Event ID")
        Spacer(modifier = Modifier.height(9.dp))
        InputField(value = username, onValueChange = { username = it }, label = "Username")
        Spacer(modifier = Modifier.height(9.dp))
        InputField(value = pin, onValueChange = { pin = it }, label = "PIN", isPassword = true)
        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Sign in",
            onClick = { onLogin(eventId.trim(), username.trim(), pin) },
            enabled = eventId.isNotBlank() && username.isNotBlank() && pin.isNotBlank() &&
                    state !is LoginState.Loading,
            isLoading = state is LoginState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("OR", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        EventSnapOutlinedButton(text = "Scan QR code", onClick = onScanQr)

        if (state is LoginState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.message,
                color = ErrorColor,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UnifiedLoginScreenPreview() {
    UnifiedLoginContent(
        adminState = AuthState.Idle,
        photographerState = LoginState.Idle,
        onAdminLogin = { _, _ -> },
        onAdminCompleteFirstLogin = { _, _, _ -> },
        onAdminRequestAccess = {},
        onAdminClearState = {},
        onPhotographerLogin = { _, _, _ -> },
        onBack = {}
    )
}