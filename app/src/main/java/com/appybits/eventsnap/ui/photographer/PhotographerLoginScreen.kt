package com.appybits.eventsnap.ui.photographer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.PhotographerTheme
import com.appybits.eventsnap.ui.theme.ErrorColor

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val eventId: String, val photographerId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class PhotographerLoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginState>(LoginState.Idle)
    val uiState: StateFlow<LoginState> = _uiState.asStateFlow()

    fun login(eventId: String, username: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = LoginState.Loading
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }

                val eventSnapshot = firestore.collection("events").document(eventId).get().await()
                if (!eventSnapshot.exists()) {
                    _uiState.value = LoginState.Error("Event not found")
                    return@launch
                }
                
                val status = eventSnapshot.getString("status")
                if (status != "ACTIVE") {
                    _uiState.value = LoginState.Error("This event has ended. Contact studio.")
                    return@launch
                }

                val photographersRef = firestore.collection("events")
                    .document(eventId)
                    .collection("photographers")

                val querySnapshot = photographersRef
                    .whereEqualTo("username", username)
                    .whereEqualTo("pin", pin)
                    .get()
                    .await()

                if (!querySnapshot.isEmpty) {
                    val photographerId = querySnapshot.documents[0].id
                    _uiState.value = LoginState.Success(eventId, photographerId)
                } else {
                    _uiState.value = LoginState.Error("Invalid credentials")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = LoginState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginState.Idle
    }
}

@Composable
fun PhotographerLoginScreen(
    viewModel: PhotographerLoginViewModel = hiltViewModel(),
    onLoginSuccess: (eventId: String, photographerId: String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) {
            val state = uiState as LoginState.Success
            onLoginSuccess(state.eventId, state.photographerId)
            viewModel.resetState()
        }
    }

    PhotographerLoginContent(
        uiState = uiState,
        onLogin = { eventId, username, pin -> viewModel.login(eventId, username, pin) },
        onBack = onBack
    )
}

@Composable
fun PhotographerLoginContent(
    uiState: LoginState,
    onLogin: (eventId: String, username: String, pin: String) -> Unit,
    onBack: () -> Unit
) {
    var eventId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    PhotographerTheme {
        Scaffold(
            topBar = { EventSnapTopBar(title = "Photographer sign in", onBack = onBack) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InputField(value = eventId, onValueChange = { eventId = it }, label = "Event ID")
                Spacer(modifier = Modifier.height(9.dp))
                InputField(value = username, onValueChange = { username = it }, label = "Username")
                Spacer(modifier = Modifier.height(9.dp))
                InputField(value = pin, onValueChange = { pin = it }, label = "PIN", isPassword = true)
                Spacer(modifier = Modifier.height(24.dp))
                
                PrimaryButton(
                    text = "Sign in",
                    onClick = { onLogin(eventId, username, pin) },
                    enabled = eventId.isNotBlank() && username.isNotBlank() && pin.isNotBlank(),
                    isLoading = uiState is LoginState.Loading
                )
                if (uiState is LoginState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = uiState.message, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhotographerLoginScreenPreview() {
    PhotographerLoginContent(uiState = LoginState.Idle, onLogin = { _, _, _ -> }, onBack = {})
}
