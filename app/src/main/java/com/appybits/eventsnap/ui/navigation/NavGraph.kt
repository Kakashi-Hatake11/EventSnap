package com.appybits.eventsnap.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.appybits.eventsnap.ui.admin.*
import com.appybits.eventsnap.ui.guest.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.serialization.Serializable

@Serializable
data object UnifiedLoginRoute : NavKey

@Serializable
data object GuestRoute : NavKey

@Serializable
data class GuestSelfieRoute(val eventId: String, val guestId: String) : NavKey

@Serializable
data class GuestDashboardRoute(val eventId: String, val guestId: String) : NavKey

@Serializable
data class GuestPhotoViewerRoute(val photoUrl: String, val allowDownload: Boolean) : NavKey

@Serializable
data class GuestPrintRoute(val eventId: String, val guestId: String, val photoIds: List<String>, val pricePerPrint: Double) : NavKey

@Composable
fun NavGraph(
    initialRole: String? = null,
    initialEventId: String? = null,
    initialUserId: String? = null,
    onRoleSelected: (String?, String?, String?) -> Unit = { _, _, _ -> }
) {
    val authViewModel: AdminAuthViewModel = hiltViewModel()

    // Guests and photographers read Firestore as anonymous users, and the security rules need a
    // signed-in user. Make sure a session exists before any screen starts querying.
    var sessionReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A saved "admin" role with no real studio session is stale; forget it.
        if (initialRole == "admin" && !authViewModel.isUserLoggedIn()) {
            onRoleSelected(null, null, null)
        }
        authViewModel.ensureSession()
        sessionReady = true
    }

    val initialRoute = when (initialRole) {
        "admin" -> if (authViewModel.isUserLoggedIn()) AdminDashboardRoute else GuestRoute
        "photographer" -> {
            if (initialEventId != null && initialUserId != null) {
                PhotographerUploadQueueRoute(initialEventId, initialUserId)
            } else GuestRoute
        }
        "guest" -> {
            if (initialEventId != null && initialUserId != null) {
                GuestDashboardRoute(initialEventId, initialUserId)
            } else GuestRoute
        }
        else -> GuestRoute
    }
    val backStack = rememberNavBackStack(initialRoute)
    val activity = LocalActivity.current
    val context = LocalContext.current

    if (!sessionReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size <= 1) {
                activity?.finish()
            } else {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = entryProvider {
            entry<UnifiedLoginRoute> {
                com.appybits.eventsnap.ui.admin.UnifiedLoginScreen(
                    onAdminLoginSuccess = {
                        com.appybits.eventsnap.utils.NotificationHelper.showWelcomeNotification(context, "Studio Admin")
                        // Save this device's push token now that a real account is signed in.
                        FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token -> authViewModel.saveFcmToken(token) }
                        onRoleSelected("admin", null, null)
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(AdminDashboardRoute)
                    },
                    onPhotographerLoginSuccess = { eventId, photographerId ->
                        com.appybits.eventsnap.utils.NotificationHelper.showWelcomeNotification(context, "Photographer")
                        onRoleSelected("photographer", eventId, photographerId)
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(PhotographerUploadQueueRoute(eventId, photographerId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AdminDashboardRoute> {
                AdminDashboardScreen(
                    onLogout = {
                        onRoleSelected(null, null, null)
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(GuestRoute)
                    },
                    onCreateEvent = {
                        backStack.add(EventEditRoute("new"))
                    },
                    onEventClick = { eventId ->
                        backStack.add(AdminEventDetailsRoute(eventId))
                    }
                )
            }
            entry<AdminEventDetailsRoute> { route ->
                AdminEventDetailsScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() },
                    onEditEvent = { backStack.add(EventEditRoute(route.eventId)) },
                    onSettings = { backStack.add(EventSettingsRoute(route.eventId)) },
                    onQRCode = { backStack.add(EventQRCodeRoute(route.eventId)) },
                    onManagePhotographers = { backStack.add(ManagePhotographersRoute(route.eventId)) },
                    onAnalytics = { backStack.add(EventAnalyticsRoute(route.eventId)) },
                    onUploadPhotos = { backStack.add(PhotographerUploadQueueRoute(route.eventId, "admin")) },
                    onViewGallery = { backStack.add(SharedGalleryRoute(route.eventId, "admin")) },
                    onDeleteSuccess = {
                        while ((backStack.size > 1) && (backStack.lastOrNull() !is AdminDashboardRoute)) {
                            backStack.removeLastOrNull()
                        }
                    }
                )
            }
            entry<EventEditRoute> { route ->
                EventEditScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() },
                    onSaveSuccess = {
                        while ((backStack.size > 1) && (backStack.lastOrNull() !is AdminDashboardRoute)) {
                            backStack.removeLastOrNull()
                        }
                    }
                )
            }
            entry<EventSettingsRoute> { route ->
                EventSettingsScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<EventQRCodeRoute> { route ->
                EventQRCodeScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<ManagePhotographersRoute> { route ->
                ManagePhotographersScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<EventAnalyticsRoute> { route ->
                EventAnalyticsScreen(
                    eventId = route.eventId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<PhotographerUploadQueueRoute> { route ->
                com.appybits.eventsnap.ui.photographer.PhotographerUploadQueueScreen(
                    eventId = route.eventId,
                    photographerId = route.photographerId,
                    onViewGallery = { backStack.add(SharedGalleryRoute(route.eventId, "photographer")) },
                    onLogout = {
                        if (route.photographerId == "admin") {
                            backStack.removeLastOrNull()
                        } else {
                            onRoleSelected(null, null, null)
                            while (backStack.size > 0) backStack.removeLastOrNull()
                            backStack.add(GuestRoute)
                        }
                    }
                )
            }
            entry<SharedGalleryRoute> { route ->
                com.appybits.eventsnap.ui.admin.SharedGalleryScreen(
                    eventId = route.eventId,
                    role = route.role,
                    onBack = { backStack.removeLastOrNull() },
                    onPhotoClick = { photoUrl ->
                        backStack.add(GuestPhotoViewerRoute(photoUrl, allowDownload = true))
                    }
                )
            }
            entry<GuestRoute> {
                GuestJoinScreen(
                    onJoinSuccess = { eventId, guestId ->
                        com.appybits.eventsnap.utils.NotificationHelper.showWelcomeNotification(context, "Guest")
                        onRoleSelected("guest", eventId, guestId)
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(GuestSelfieRoute(eventId, guestId))
                    },
                    onLoginClick = { backStack.add(UnifiedLoginRoute) }
                )
            }
            entry<GuestSelfieRoute> { route ->
                GuestSelfieCaptureScreen(
                    eventId = route.eventId,
                    guestId = route.guestId,
                    onUploadSuccess = {
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(GuestDashboardRoute(route.eventId, route.guestId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<GuestDashboardRoute> { route ->
                GuestDashboardScreen(
                    eventId = route.eventId,
                    guestId = route.guestId,
                    onLogout = {
                        onRoleSelected(null, null, null)
                        while (backStack.size > 0) backStack.removeLastOrNull()
                        backStack.add(GuestRoute)
                    },
                    onAddSelfie = {
                        backStack.add(GuestSelfieRoute(route.eventId, route.guestId))
                    },
                    onPhotoClick = { photo, allowDownload ->
                        backStack.add(GuestPhotoViewerRoute(photo.downloadUrl, allowDownload))
                    },
                    onPrintRequest = { photos, pricePerPrint ->
                        val photoIds = photos.map { it.id }
                        backStack.add(GuestPrintRoute(route.eventId, route.guestId, photoIds, pricePerPrint))
                    }
                )
            }
            entry<GuestPhotoViewerRoute> { route ->
                GuestPhotoViewerScreen(
                    photoUrl = route.photoUrl,
                    allowDownload = route.allowDownload,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<GuestPrintRoute> { route ->
                GuestPrintScreen(
                    eventId = route.eventId,
                    guestId = route.guestId,
                    photoIds = route.photoIds,
                    pricePerPrint = route.pricePerPrint,
                    onSuccess = {
                        backStack.removeLastOrNull()
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

@Serializable
data class AdminEventDetailsRoute(val eventId: String) : NavKey

@Serializable
data class PhotographerUploadQueueRoute(val eventId: String, val photographerId: String) : NavKey

@Serializable
data class SharedGalleryRoute(val eventId: String, val role: String) : NavKey