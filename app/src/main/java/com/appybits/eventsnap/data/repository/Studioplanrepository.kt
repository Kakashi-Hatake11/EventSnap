package com.appybits.eventsnap.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A studio's plan and how much of it has been used. Set by your team; read-only in the app. */
data class StudioPlan(
    val planName: String = "",
    val eventsAllowed: Long = 0,
    val eventsUsed: Long = 0,
    val storageLimitBytes: Long = 0,
    val storageUsedBytes: Long = 0,
    val expiresAtMillis: Long = 0,
    val active: Boolean = false
) {
    val unlimitedEvents: Boolean get() = eventsAllowed >= 100_000
    val isExpired: Boolean get() = expiresAtMillis in 1..System.currentTimeMillis()

    val eventsFraction: Float
        get() = if (eventsAllowed > 0) (eventsUsed.toFloat() / eventsAllowed).coerceIn(0f, 1f) else 0f

    val storageFraction: Float
        get() = if (storageLimitBytes > 0) (storageUsedBytes.toFloat() / storageLimitBytes).coerceIn(0f, 1f) else 0f

    /** Why a new event can't be created right now, or null if it can. */
    val createBlockedReason: String?
        get() = when {
            !active -> "Your plan is inactive. Contact support."
            isExpired -> "Your plan has expired. Contact support to renew."
            !unlimitedEvents && eventsUsed >= eventsAllowed ->
                "You've used all the events in your plan. Contact support to add more."
            else -> null
        }

    val canCreateEvent: Boolean get() = createBlockedReason == null
}

@Singleton
class StudioPlanRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /** Emits the signed-in studio's plan, or null if it has no plan record yet. */
    fun observePlan(): Flow<StudioPlan?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("studioAccounts").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    StudioPlan(
                        planName = snap.getString("planName") ?: "Plan",
                        eventsAllowed = snap.getLong("eventsAllowed") ?: 0,
                        eventsUsed = snap.getLong("eventsUsed") ?: 0,
                        storageLimitBytes = snap.getLong("storageLimitBytes") ?: 0,
                        storageUsedBytes = snap.getLong("storageUsedBytes") ?: 0,
                        expiresAtMillis = snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0,
                        active = snap.getBoolean("active") == true
                    )
                )
            }
        awaitClose { registration.remove() }
    }
}