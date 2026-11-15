package com.appybits.eventsnap.ui.guest

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.appybits.eventsnap.data.model.Photo
import com.appybits.eventsnap.ui.components.*
import com.appybits.eventsnap.ui.theme.EventSnapTheme

@Composable
fun GuestDashboardScreen(
    eventId: String,
    guestId: String,
    onLogout: () -> Unit,
    onAddSelfie: () -> Unit,
    /** Photo tapped, plus whether the studio allows downloading. */
    onPhotoClick: (photo: Photo, allowDownload: Boolean) -> Unit,
    /** Photos chosen for printing, plus the studio's price per print. */
    onPrintRequest: (photos: List<Photo>, pricePerPrint: Double) -> Unit,
    viewModel: GuestDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(eventId, guestId) { viewModel.loadData(eventId, guestId) }
    GuestDashboardContent(
        state = state,
        onLogout = onLogout,
        onAddSelfie = onAddSelfie,
        onPhotoClick = onPhotoClick,
        onPrintRequest = onPrintRequest,
        onRetry = { viewModel.loadData(eventId, guestId) },
        onRefresh = { viewModel.refreshPhotos(eventId, guestId) },
        onErrorShown = { viewModel.clearError() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GuestDashboardContent(
    state: GuestDashboardState,
    onLogout: () -> Unit,
    onAddSelfie: () -> Unit,
    onPhotoClick: (Photo, Boolean) -> Unit,
    onPrintRequest: (List<Photo>, Double) -> Unit,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onErrorShown: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedPhotos = remember { mutableStateListOf<Photo>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Only a closed event sends the guest back to the join screen.
    LaunchedEffect(state.eventEnded) {
        if (state.eventEnded) {
            Toast.makeText(context, "This event has ended. Contact studio.", Toast.LENGTH_LONG).show()
            onLogout()
        }
    }

    // A refresh problem after photos are already showing: tell them, but keep them signed in.
    LaunchedEffect(state.error) {
        if (state.error != null && state.event != null) {
            Toast.makeText(context, state.error, Toast.LENGTH_LONG).show()
            onErrorShown()
        }
    }

    val allowDownload = state.event?.allowDownload == true
    val allowPrint = state.event?.allowPrint == true
    val pricePerPrint = state.event?.pricePerPrint ?: 0.0

    EventSnapTheme {
        EventSnapBackground {
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Logout") },
                    text = { Text("Are you sure you want to exit?") },
                    confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Yes", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("No") } }
                )
            }

            Scaffold(
                topBar = {
                    EventSnapTopBar(
                        title = if (isSelectionMode) "${selectedPhotos.size} selected" else (state.event?.name ?: "Event"),
                        actions = {
                            if (isSelectionMode) {
                                TextButton(onClick = { isSelectionMode = false; selectedPhotos.clear() }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground) }
                            } else {
                                TextButton(onClick = onRefresh) { Text("Refresh", color = MaterialTheme.colorScheme.onBackground) }
                                TextButton(onClick = { showLogoutDialog = true }) { Text("Logout", color = MaterialTheme.colorScheme.onBackground) }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                        Column(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                            if (allowDownload) {
                                PrimaryButton(
                                    text = "Download (${selectedPhotos.size})",
                                    onClick = {
                                        selectedPhotos.forEach { downloadPhoto(context, it.downloadUrl) }
                                        selectedPhotos.clear()
                                        isSelectionMode = false
                                    }
                                )
                                Spacer(modifier = Modifier.height(9.dp))
                            }
                            if (allowPrint) {
                                EventSnapOutlinedButton(
                                    text = "Request prints",
                                    onClick = { onPrintRequest(selectedPhotos.toList(), pricePerPrint) }
                                )
                            }
                        }
                    } else if (!isSelectionMode) {
                        Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                            PrimaryButton(text = "Take selfie", onClick = onAddSelfie)
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
                        Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("My photos", style = MaterialTheme.typography.titleMedium) })
                        Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Common", style = MaterialTheme.typography.titleMedium) })
                    }

                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (state.error != null && state.event == null) {
                        // First load failed (for example no connection): offer a retry.
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(state.error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            PrimaryButton(text = "Try again", onClick = onRetry)
                        }
                    } else {
                        val photosToShow = if (selectedTabIndex == 0) state.myPhotos else state.commonPhotos
                        if (photosToShow.isEmpty()) {
                            EmptyState(
                                title = if (selectedTabIndex == 0) "No photos matched yet." else "No common photos uploaded yet.",
                                subtitle = if (selectedTabIndex == 0) "The event is still in progress. Tap Refresh to check for new photos." else null
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(photosToShow) { photo ->
                                    val isSelected = selectedPhotos.contains(photo)
                                    Box(
                                        modifier = Modifier.aspectRatio(1f).combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    if (isSelected) selectedPhotos.remove(photo) else selectedPhotos.add(photo)
                                                } else {
                                                    onPhotoClick(photo, allowDownload)
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
                                                .size(300) // load low memory thumbnail for grid
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            loading = {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
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
}

@Preview(showBackground = true)
@Composable
fun GuestDashboardScreenPreview() {
    GuestDashboardContent(
        state = GuestDashboardState(),
        onLogout = {},
        onAddSelfie = {},
        onPhotoClick = { _, _ -> },
        onPrintRequest = { _, _ -> }
    )
}