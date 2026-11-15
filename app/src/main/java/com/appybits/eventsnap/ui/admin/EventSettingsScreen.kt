package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme

@Composable
fun EventSettingsScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    val event by viewModel.event.collectAsState()
    LaunchedEffect(eventId) { viewModel.loadEvent(eventId) }

    EventSettingsContent(
        event = event,
        onUpdateSettings = { allowDownload, allowPrint, pricePerPrint, isActive ->
            viewModel.updateSettings(allowDownload, allowPrint, pricePerPrint, isActive)
        },
        onBack = onBack
    )
}

@Composable
fun EventSettingsContent(
    event: Event?,
    onUpdateSettings: (Boolean, Boolean, Double, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = { EventSnapTopBar(title = event?.name ?: "Event Settings", subtitle = "Settings", onBack = onBack) },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                event?.let { ev ->
                    var allowDownload by remember { mutableStateOf(ev.allowDownload) }
                    var allowPrint by remember { mutableStateOf(ev.allowPrint) }
                    var pricePerPrint by remember { mutableStateOf(ev.pricePerPrint.toString()) }
                    var isActive by remember { mutableStateOf(ev.status == "ACTIVE") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "CONTROLS & MONETIZATION", 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                        )
                        SettingRow(
                            label = "Event Active Status",
                            subtitle = "When deactivated, guests and photographers receive real-time lockout alerts",
                            checked = isActive,
                            onCheckedChange = { 
                                isActive = it
                                onUpdateSettings(allowDownload, allowPrint, pricePerPrint.toDoubleOrNull() ?: 0.0, isActive)
                            }
                        )
                        SettingRow(
                            label = "High-Resolution Guest Downloads",
                            subtitle = "Allow attendees to save uncompressed print-ready RAW/JPEG originals",
                            checked = allowDownload,
                            onCheckedChange = { 
                                allowDownload = it
                                onUpdateSettings(allowDownload, allowPrint, pricePerPrint.toDoubleOrNull() ?: 0.0, isActive)
                            }
                        )
                        SettingRow(
                            label = "Print Requests & Studio E-Commerce",
                            subtitle = "Enable attendees to order physical lab prints directly from the gallery",
                            checked = allowPrint,
                            onCheckedChange = { 
                                allowPrint = it
                                onUpdateSettings(allowDownload, allowPrint, pricePerPrint.toDoubleOrNull() ?: 0.0, isActive)
                            },
                            bottomContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Price per Print (₹ INR):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    InputField(
                                        value = pricePerPrint,
                                        onValueChange = { 
                                            pricePerPrint = it
                                            onUpdateSettings(allowDownload, allowPrint, pricePerPrint.toDoubleOrNull() ?: 0.0, isActive)
                                        },
                                        label = "Amount",
                                        modifier = Modifier.width(100.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { 
                                            keyboardController?.hide()
                                        })
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EventSettingsScreenPreview() {
    EventSettingsContent(
        event = Event(name = "Anna's Wedding", allowDownload = true, allowPrint = true, pricePerPrint = 25.0, status = "ACTIVE"),
        onUpdateSettings = { _, _, _, _ -> },
        onBack = {}
    )
}
