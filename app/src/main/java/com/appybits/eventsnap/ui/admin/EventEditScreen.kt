package com.appybits.eventsnap.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.ErrorColor
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import java.text.SimpleDateFormat
import java.util.Locale

private const val MIN_EVENT_ID_LENGTH = 3
private const val MAX_EVENT_ID_LENGTH = 40

@Composable
fun EventEditScreen(
    eventId: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EventDetailsViewModel = hiltViewModel()
) {
    val event by viewModel.event.collectAsState()
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) { viewModel.loadEvent(eventId) }

    EventEditContent(
        eventId = eventId,
        eventName = event?.name ?: "",
        clientName = event?.clientName ?: "",
        venue = event?.venue ?: "",
        eventDate = if (eventId == "new") System.currentTimeMillis() else (event?.date ?: System.currentTimeMillis()),
        coverImageUrl = event?.coverImageUrl ?: "",
        isSaving = isSaving,
        errorMessage = saveError,
        onBack = onBack,
        onSaveEvent = { customId, name, clientName, venue, selectedDate, coverUri ->
            isSaving = true
            saveError = null
            viewModel.saveEvent(
                customId = customId,
                name = name,
                clientName = clientName,
                venue = venue,
                date = selectedDate,
                coverImageUri = coverUri,
                onSuccess = {
                    isSaving = false
                    onSaveSuccess()
                },
                onError = { message ->
                    isSaving = false
                    saveError = message
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditContent(
    eventId: String,
    eventName: String,
    clientName: String,
    venue: String,
    eventDate: Long,
    coverImageUrl: String,
    isSaving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSaveEvent: (String, String, String, String, Long, Uri?) -> Unit
) {
    var customId by remember { mutableStateOf("") }
    var name by remember(eventName) { mutableStateOf(eventName) }
    var hostName by remember(clientName) { mutableStateOf(clientName) }
    var eventVenue by remember(venue) { mutableStateOf(venue) }
    var selectedDate by remember(eventDate) { mutableStateOf(eventDate) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)

    val isNew = eventId == "new"
    val idTooShort = customId.isNotEmpty() && customId.length < MIN_EVENT_ID_LENGTH
    val canSave = name.isNotBlank() && hostName.isNotBlank() && eventVenue.isNotBlank() &&
            (!isNew || customId.length >= MIN_EVENT_ID_LENGTH) &&
            !isSaving

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) selectedCoverUri = uri }
    )
    val pickImage = {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = if (isNew) "New event" else "Edit event",
                        onBack = onBack
                    )
                },
                bottomBar = {
                    Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                        PrimaryButton(
                            text = "Save event",
                            onClick = {
                                if (canSave) onSaveEvent(customId, name.trim(), hostName.trim(), eventVenue.trim(), selectedDate, selectedCoverUri)
                            },
                            enabled = canSave,
                            isLoading = isSaving
                        )
                    }
                },
                containerColor = Color.Transparent
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedCoverUri != null || coverImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedCoverUri ?: coverImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { pickImage() }
                        )
                    } else {
                        EmptyState(
                            title = "Add cover image",
                            modifier = Modifier
                                .height(150.dp)
                                .clickable { pickImage() }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isNew) {
                        InputField(
                            value = customId,
                            // Only a-z, 0-9 and "-" are allowed: anything else (like "/")
                            // is not a valid Firestore document ID.
                            onValueChange = { input ->
                                customId = input
                                    .lowercase()
                                    .replace(" ", "-")
                                    .filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' }
                                    .take(MAX_EVENT_ID_LENGTH)
                            },
                            label = "Custom Event ID (e.g. anna-wedding)",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        FieldError(
                            if (idTooShort) "Use at least $MIN_EVENT_ID_LENGTH characters" else null
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    InputField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Event Name",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    InputField(
                        value = hostName,
                        onValueChange = { hostName = it },
                        label = "Client / Host Name",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    InputField(
                        value = eventVenue,
                        onValueChange = { eventVenue = it },
                        label = "Event Hall / Venue",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                            keyboardController?.hide()
                        })
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedDate)
                    OutlinedTextField(
                        value = formattedDate,
                        onValueChange = { },
                        label = { Text("Event Date") },
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                                    showDatePicker = false
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = ErrorColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun EventEditScreenPreview() {
    EventEditContent(
        eventId = "1",
        eventName = "Anna's Wedding",
        clientName = "Anna",
        venue = "Grand Palace",
        eventDate = System.currentTimeMillis(),
        coverImageUrl = "",
        isSaving = false,
        errorMessage = null,
        onBack = {},
        onSaveEvent = { _, _, _, _, _, _ -> }
    )
}