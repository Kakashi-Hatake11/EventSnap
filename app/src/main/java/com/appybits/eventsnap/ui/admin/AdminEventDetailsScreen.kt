package com.appybits.eventsnap.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import com.appybits.eventsnap.ui.theme.ErrorColor

@Composable
fun AdminEventDetailsScreen(
    eventId: String,
    onBack: () -> Unit,
    onEditEvent: () -> Unit,
    onSettings: () -> Unit,
    onQRCode: () -> Unit,
    onManagePhotographers: () -> Unit,
    onAnalytics: () -> Unit,
    onUploadPhotos: () -> Unit,
    onViewGallery: () -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    AdminEventDetailsContent(
        onBack = onBack,
        onEditEvent = onEditEvent,
        onSettings = onSettings,
        onQRCode = onQRCode,
        onManagePhotographers = onManagePhotographers,
        onAnalytics = onAnalytics,
        onUploadPhotos = onUploadPhotos,
        onViewGallery = onViewGallery,
        onDeleteEvent = {
            viewModel.deleteEvent(eventId) {
                onDeleteSuccess()
            }
        }
    )
}

@Composable
fun AdminEventDetailsContent(
    onBack: () -> Unit,
    onEditEvent: () -> Unit,
    onSettings: () -> Unit,
    onQRCode: () -> Unit,
    onManagePhotographers: () -> Unit,
    onAnalytics: () -> Unit,
    onUploadPhotos: () -> Unit,
    onViewGallery: () -> Unit,
    onDeleteEvent: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    EventSnapTheme {
        EventSnapBackground {
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Event") },
                    text = { Text("Are you sure you want to delete this event? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = { showDeleteDialog = false; onDeleteEvent() }) {
                            Text("Delete", color = ErrorColor)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Scaffold(
                topBar = { EventSnapTopBar(title = "Event Options", onBack = onBack) },
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    EventSnapOutlinedButton("Edit Event Details", onEditEvent)
                    EventSnapOutlinedButton("Controls & Monetization", onSettings)
                    EventSnapOutlinedButton("View QR Code", onQRCode)
                    EventSnapOutlinedButton("Manage Photographers", onManagePhotographers)
                    EventSnapOutlinedButton("Event Analytics", onAnalytics)
                    EventSnapOutlinedButton("Upload Photos", onUploadPhotos)
                    EventSnapOutlinedButton("View Gallery", onViewGallery)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                    ) {
                        Text("Delete Event")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminEventDetailsScreenPreview() {
    AdminEventDetailsContent(
        onBack = {}, onEditEvent = {}, onSettings = {}, onQRCode = {},
        onManagePhotographers = {}, onAnalytics = {}, onUploadPhotos = {}, onViewGallery = {}, onDeleteEvent = {}
    )
}
