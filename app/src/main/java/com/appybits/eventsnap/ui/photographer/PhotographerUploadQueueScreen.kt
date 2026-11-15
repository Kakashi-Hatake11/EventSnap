package com.appybits.eventsnap.ui.photographer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.appybits.eventsnap.data.local.UploadDao
import com.appybits.eventsnap.data.local.UploadItem
import com.appybits.eventsnap.data.local.UploadStatus
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.PhotographerTheme
import com.appybits.eventsnap.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotographerUploadQueueViewModel @Inject constructor(
    private val uploadDao: UploadDao,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _eventStatus = MutableStateFlow<String?>("ACTIVE")
    val eventStatus = _eventStatus.asStateFlow()

    fun listenToEventStatus(eventId: String) {
        firestore.collection("events").document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val status = snapshot?.getString("status")
                _eventStatus.value = status
            }
    }

    fun getUploadsStream(eventId: String) = uploadDao.getUploadsForEvent(eventId)

    fun queueUploads(eventId: String, uris: List<Uri>, photographerId: String, isCommon: Boolean, context: android.content.Context) {
        // Take persistable URI permissions immediately upon selection so the Worker can read them later
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
        }

        viewModelScope.launch {
            uris.forEach { uri ->
                uploadDao.insert(
                    UploadItem(
                        uri = uri.toString(),
                        eventId = eventId,
                        photographerId = photographerId,
                        isCommon = isCommon,
                        status = UploadStatus.PENDING
                    )
                )
            }
        }
    }
}

@Composable
fun PhotographerUploadQueueScreen(
    eventId: String,
    photographerId: String,
    viewModel: PhotographerUploadQueueViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onViewGallery: () -> Unit
) {
    val context = LocalContext.current
    val uploads by remember(eventId) { viewModel.getUploadsStream(eventId) }
        .collectAsState(initial = emptyList())

    val eventStatus by viewModel.eventStatus.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.listenToEventStatus(eventId)
    }

    LaunchedEffect(eventStatus) {
        if (eventStatus != null && eventStatus != "ACTIVE") {
            android.widget.Toast.makeText(context, "This event has ended. Contact studio.", android.widget.Toast.LENGTH_LONG).show()
            onLogout()
        }
    }

    PhotographerUploadQueueContent(
        uploads = uploads,
        onQueueUploads = { uris, isCommon ->
            viewModel.queueUploads(eventId, uris, photographerId, isCommon, context)
            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("UploadWorker", ExistingWorkPolicy.APPEND_OR_REPLACE, uploadWorkRequest)
        },
        onViewGallery = onViewGallery,
        onLogout = onLogout
    )
}

@Composable
fun PhotographerUploadQueueContent(
    uploads: List<UploadItem>,
    onQueueUploads: (List<Uri>, Boolean) -> Unit,
    onViewGallery: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isCommonPhotos by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                // IMPORTANT: Take persistable URI permissions HERE, while the UI process still has temporary access.
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        // Some URI providers don't support persistable permissions.
                        // If it fails, the background worker might fail to read it later.
                    }
                }
                onQueueUploads(uris, isCommonPhotos)
            }
        }
    )

    PhotographerTheme {
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Yes", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("No") } }
            )
        }

        if (showOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showOptionsDialog = false },
                title = { Text("Select Folder") },
                text = {
                    Column {
                        SettingRow(label = "Upload to Common folder", checked = isCommonPhotos, onCheckedChange = { isCommonPhotos = it })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showOptionsDialog = false
                        photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Proceed") }
                },
                dismissButton = { TextButton(onClick = { showOptionsDialog = false }) { Text("Cancel") } }
            )
        }

        val completed = uploads.count { it.status == UploadStatus.COMPLETED }
        val uploading = uploads.count { it.status == UploadStatus.UPLOADING }
        val pending = uploads.count { it.status == UploadStatus.PENDING }

        Scaffold(
            topBar = {
                EventSnapTopBar(
                    title = "Uploads",
                    subtitle = "$completed done · $uploading uploading · $pending waiting",
                    actions = {
                        TextButton(onClick = onViewGallery) {
                            Text("Gallery", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showLogoutDialog = true }) {
                            Text("Logout", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            },
            bottomBar = {
                Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                    PrimaryButton(text = "Pick photos", onClick = { showOptionsDialog = true })
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (uploads.isEmpty()) {
                EmptyState(title = "No uploads in queue", modifier = Modifier.padding(padding))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(uploads) { item ->
                        ListTile(
                            title = "Photo ID: ${item.id}",
                            subtitle = if (item.isCommon) "Common" else "Guests",
                            statusText = item.status.name,
                            statusColor = when (item.status) {
                                UploadStatus.COMPLETED -> com.appybits.eventsnap.ui.theme.SuccessColor
                                UploadStatus.FAILED -> com.appybits.eventsnap.ui.theme.ErrorColor
                                else -> MaterialTheme.colorScheme.primary
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
fun PhotographerUploadQueueScreenPreview() {
    PhotographerUploadQueueContent(
        uploads = listOf(
            UploadItem(id = 1, uri = "", eventId = "", photographerId = "", isCommon = false, status = UploadStatus.UPLOADING),
            UploadItem(id = 2, uri = "", eventId = "", photographerId = "", isCommon = true, status = UploadStatus.COMPLETED)
        ),
        onQueueUploads = { _, _ -> }, onViewGallery = {}, onLogout = {}
    )
}
