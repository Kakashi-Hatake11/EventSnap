package com.appybits.eventsnap.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** What a studio fills in on the "Request account" form. */
data class StudioRequest(
    val name: String,
    val studioName: String = "",
    val phone: String,
    val email: String,
    val country: String,
    val state: String
)

@Singleton
class StudioRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * Saves the request to `studioRequests/{sha256(email)}`.
     * The document ID is derived from the email, so the same email can't flood the
     * collection: Firestore rules only allow creating a document that doesn't exist yet.
     */
    suspend fun submit(request: StudioRequest): Result<Unit> =
        try {
            val data = mutableMapOf<String, Any>(
                "name" to request.name,
                "phone" to request.phone,
                "email" to request.email,
                "country" to request.country,
                "state" to request.state,
                "status" to "new",
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (request.studioName.isNotBlank()) data["studioName"] = request.studioName

            // Without a timeout, a write made while offline would wait forever.
            withTimeout(15_000) {
                firestore.collection("studioRequests")
                    .document(docId(request.email))
                    .set(data)
                    .await()
            }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Couldn't send your request. Check your connection and try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.failure(
                    Exception("We already have a request from this email. Our team will be in touch.")
                )
            } else {
                Result.failure(Exception("Couldn't send your request. Please try again."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't send your request. Please try again."))
        }

    private fun docId(email: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
}