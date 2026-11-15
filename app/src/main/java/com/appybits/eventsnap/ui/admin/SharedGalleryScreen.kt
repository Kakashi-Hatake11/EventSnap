package com.appybits.eventsnap.ui.admin

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.appybits.eventsnap.data.model.Photo
import com.appybits.eventsnap.data.repository.DeletePhotosOutcome
import com.appybits.eventsnap.data.repository.EventRepository
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.ErrorColor
import com.appybits.eventsnap.ui.theme.EventSnapTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedGalleryViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun loadPhotos(eventId: String) {
        viewModelScope.launch {
            _loadError.value = null
            repository.getAllPhotos(eventId)
                .catch { e -> _loadError.value = e.message ?: "Couldn't load photos." }
                .collect { list ->
                    _loadError.value = null
                    _photos.value = list
                }
        }
    }

    fun deleteSelectedPhotos(
        eventId: String,
        selectedPhotos: List<Photo>,
        onResult: (Result<DeletePhotosOutcome>) -> Unit
    ) {
        viewModelScope.launch {
            _isDeleting.value = true
            val result = repository.deletePhotos(eventId, selectedPhotos)
            _isDeleting.value = false
            onResult(result)
        }
    }
}

@Composable
fun SharedGalleryScreen(
    eventId: String,
    role: String, // "admin" or "photographer"
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: SharedGalleryViewModel = hiltViewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(eventId) {
        viewModel.loadPhotos(eventId)
    }

    SharedGalleryContent(
        photos = photos,
        role = role,
        isDeleting = isDeleting,
        loadError = loadError,
        onDeletePhotos = { selected ->
            viewModel.deleteSelectedPhotos(eventId, selected) { result ->
                val message = result.fold(
                    onSuccess = { outcome ->
                        when {
                            outcome.failed == 0 ->
                                "Deleted ${outcome.deleted} photo(s)."
                            outcome.deleted == 0 ->
                                "Couldn't delete these photos. You can only delete photos you uploaded."
                            else ->
                                "Deleted ${outcome.deleted}. ${outcome.failed} couldn't be deleted " +
                                        "(you can only delete photos you uploaded)."
                        }
                    },
                    onFailure = { it.message ?: "Couldn't delete photos." }
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        onPhotoClick = onPhotoClick,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SharedGalleryContent(
    photos: List<Photo>,
    role: String,
    isDeleting: Boolean,
    onDeletePhotos: (List<Photo>) -> Unit,
    onPhotoClick: (String) -> Unit,
    onBack: () -> Unit,
    loadError: String? = null
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedPhotos = remember { mutableStateListOf<Photo>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val commonPhotos = photos.filter { it.isCommon }
    val guestPhotos = photos.filter { !it.isCommon }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Photos") },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedPhotos.size} photo(s)? This cannot be undone." +
                            if (role != "admin") " You can only delete photos you uploaded." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePhotos(selectedPhotos.toList())
                        selectedPhotos.clear()
                        isSelectionMode = false
                    }
                ) {
                    Text("Delete", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    EventSnapTheme {
        EventSnapBackground {
            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = if (isSelectionMode) "${selectedPhotos.size} selected" else "Event Gallery",
                        onBack = onBack,
                        actions = {
                            if (isSelectionMode) {
                                TextButton(onClick = { isSelectionMode = false; selectedPhotos.clear() }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                        Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = MaterialTheme.shapes.medium,
                                enabled = !isDeleting,
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = Color.White)
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Delete (${selectedPhotos.size})", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                },
                containerColor = Color.Transparent
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Guest Photos", style = MaterialTheme.typography.titleMedium) })
                        Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Common", style = MaterialTheme.typography.titleMedium) })
                    }

                    val photosToShow = if (selectedTabIndex == 0) guestPhotos else commonPhotos

                    if (photosToShow.isEmpty()) {
                        EmptyState(
                            title = if (loadError != null) "Couldn't load photos." else "No photos here.",
                            subtitle = loadError ?: "Photos uploaded will appear here."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(photosToShow, key = { it.id }) { photo ->
                                val isSelected = selectedPhotos.contains(photo)
                                Box(
                                    modifier = Modifier.aspectRatio(1f).combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedPhotos.remove(photo) else selectedPhotos.add(photo)
                                            } else {
                                                onPhotoClick(photo.downloadUrl)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedPhotos.add(photo)
                                            }
                                        }
                                    )
                                ) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(photo.downloadUrl)
                                            .crossfade(true)
                                            .size(300)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        // A photo whose file is missing shows a placeholder instead of a blank tile.
                                        error = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.BrokenImage,
                                                    contentDescription = "Photo unavailable",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                    if (isSelected) {
                                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {}
                                        Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SharedGalleryScreenPreview() {
    SharedGalleryContent(
        photos = listOf(),
        role = "admin",
        isDeleting = false,
        onDeletePhotos = {},
        onPhotoClick = {},
        onBack = {}
    )
}