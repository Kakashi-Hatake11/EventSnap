package com.appybits.eventsnap.ui.guest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appybits.eventsnap.data.repository.GuestRepository
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GuestPrintState {
    object Idle : GuestPrintState()
    object Submitting : GuestPrintState()
    object Success : GuestPrintState()
    data class Error(val message: String) : GuestPrintState()
}

@HiltViewModel
class GuestPrintViewModel @Inject constructor(
    private val repository: GuestRepository
) : ViewModel() {
    private val _state = MutableStateFlow<GuestPrintState>(GuestPrintState.Idle)
    val state: StateFlow<GuestPrintState> = _state
    
    fun submitPrintRequest(eventId: String, guestId: String, photoIds: List<String>, quantityPerPhoto: Int, pricePerPrint: Double) {
        viewModelScope.launch {
            _state.value = GuestPrintState.Submitting
            try {
                repository.submitPrintRequest(eventId, guestId, photoIds, quantityPerPhoto, pricePerPrint)
                _state.value = GuestPrintState.Success
            } catch (e: Exception) {
                _state.value = GuestPrintState.Error(e.message ?: "Failed to submit request")
            }
        }
    }
}

@Composable
fun GuestPrintScreen(
    eventId: String,
    guestId: String,
    photoIds: List<String>,
    pricePerPrint: Double,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: GuestPrintViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state) {
        if (state is GuestPrintState.Success) {
            com.appybits.eventsnap.utils.NotificationHelper.showPrintRequestNotification(context, photoIds.size)
            onSuccess()
        }
    }
    GuestPrintContent(
        photoIds = photoIds, pricePerPrint = pricePerPrint, state = state,
        onSubmitPrintRequest = { qty -> viewModel.submitPrintRequest(eventId, guestId, photoIds, qty, pricePerPrint) },
        onBack = onBack
    )
}

@Composable
fun GuestPrintContent(
    photoIds: List<String>, pricePerPrint: Double, state: GuestPrintState,
    onSubmitPrintRequest: (Int) -> Unit, onBack: () -> Unit
) {
    var quantityPerPhoto by remember { mutableStateOf(1) }
    val totalQty = quantityPerPhoto * photoIds.size
    val totalPrice = totalQty * pricePerPrint

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = { EventSnapTopBar(title = "Print request", onBack = onBack) },
                bottomBar = {
                    Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                        PrimaryButton(
                            text = "Submit request",
                            onClick = { onSubmitPrintRequest(quantityPerPhoto) },
                            isLoading = state is GuestPrintState.Submitting
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
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    ListTile(
                        title = "${photoIds.size} photos selected",
                        subtitle = "₹ $pricePerPrint each",
                        statusText = "Qty $quantityPerPhoto"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text("₹ $totalPrice", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Change quantity per photo:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        EventSnapOutlinedButton(text = "-", onClick = { if (quantityPerPhoto > 1) quantityPerPhoto-- }, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(9.dp))
                        EventSnapOutlinedButton(text = "+", onClick = { quantityPerPhoto++ }, modifier = Modifier.weight(1f))
                    }
                    if (state is GuestPrintState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GuestPrintScreenPreview() { GuestPrintContent(photoIds = listOf("1", "2"), pricePerPrint = 25.0, state = GuestPrintState.Idle, onSubmitPrintRequest = {}, onBack = {}) }
