package com.appybits.eventsnap.ui.admin

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object AdminLoginRoute : NavKey

@Serializable
data object AdminDashboardRoute : NavKey

@Serializable
data class EventEditRoute(val eventId: String) : NavKey

@Serializable
data class EventSettingsRoute(val eventId: String) : NavKey

@Serializable
data class EventQRCodeRoute(val eventId: String) : NavKey

@Serializable
data class ManagePhotographersRoute(val eventId: String) : NavKey

@Serializable
data class EventAnalyticsRoute(val eventId: String) : NavKey
