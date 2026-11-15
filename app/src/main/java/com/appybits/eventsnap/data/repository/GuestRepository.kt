package com.appybits.eventsnap.data.repository

import android.net.Uri
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.model.Guest
import com.appybits.eventsnap.data.model.Photo
import com.appybits.eventsnap.data.model.PrintRequest
import com.appybits.eventsnap.data.model.Selfie
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuestRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    /** The security rules need a signed-in user for every read, so sign in anonymously first. */
    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    suspend fun joinEventByPin(pin: String): Event {
        ensureSignedIn()
        val querySnapshot = firestore.collection("events")
            .whereEqualTo("pin", pin.trim())
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            throw Exception("Event not found with provided PIN.")
        }
        return pickActiveEvent(querySnapshot.documents.mapNotNull { it.toObject(Event::class.java) })
    }

    suspend fun joinEventByQr(qrValue: String): Event {
        ensureSignedIn()
        val querySnapshot = firestore.collection("events")
            .whereEqualTo("qrCodeValue", qrValue.trim())
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            throw Exception("Event not found with provided QR Code.")
        }
        return pickActiveEvent(querySnapshot.documents.mapNotNull { it.toObject(Event::class.java) })
    }

    // Two events can end up with the same PIN. Prefer one that is still running.
    private fun pickActiveEvent(events: List<Event>): Event {
        if (events.isEmpty()) throw Exception("Invalid event data.")
        return events.firstOrNull { it.status == "ACTIVE" }
            ?: throw Exception("This event has ended. Contact studio.")
    }

    suspend fun createGuest(eventId: String): String {
        ensureSignedIn()
        val user = auth.currentUser ?: throw Exception("Authentication failed.")

        val guestId = user.uid
        val guestRef = firestore.collection("events")
            .document(eventId)
            .collection("guests")
            .document(guestId)

        // A returning guest (same phone, same anonymous ID) already has a profile.
        // Leave it untouched so a rejoin never needs update permission and keeps their selfies.
        val existing = guestRef.get().await()
        if (!existing.exists()) {
            guestRef.set(Guest(id = guestId, eventId = eventId)).await()
        }

        return guestId
    }

    suspend fun uploadSelfie(eventId: String, guestId: String, photoUri: Uri) {
        ensureSignedIn()
        val selfieId = UUID.randomUUID().toString()
        val storagePath = "events/$eventId/selfies/$guestId/$selfieId.jpg"
        val storageRef = storage.reference.child(storagePath)

        // Explicit content type: the Storage rules only accept images.
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        storageRef.putFile(photoUri, metadata).await()

        val selfie = Selfie(
            id = selfieId,
            guestId = guestId,
            storagePath = storagePath
        )

        firestore.collection("events")
            .document(eventId)
            .collection("guests")
            .document(guestId)
            .collection("selfies")
            .document(selfieId)
            .set(selfie)
            .await()
    }

    suspend fun getEvent(eventId: String): Event {
        val doc = firestore.collection("events").document(eventId).get().await()
        return doc.toObject(Event::class.java)?.copy(id = doc.id)
            ?: throw Exception("Event not found.")
    }

    suspend fun getCommonPhotos(eventId: String): List<Photo> {
        val snapshot = firestore.collection("events")
            .document(eventId)
            .collection("photos")
            .whereEqualTo("isCommon", true)
            .get()
            .await()

        // The document ID is the photo's ID (print requests and matching rely on it).
        return snapshot.documents.mapNotNull { it.toObject(Photo::class.java)?.copy(id = it.id) }
    }

    suspend fun getMyPhotos(eventId: String, guestId: String): List<Photo> {
        val selfiesSnapshot = firestore.collection("events")
            .document(eventId)
            .collection("guests")
            .document(guestId)
            .collection("selfies")
            .get()
            .await()

        val matchedPhotoIds = selfiesSnapshot.documents.flatMap {
            it.toObject(Selfie::class.java)?.matchedPhotoIds ?: emptyList()
        }.distinct()

        if (matchedPhotoIds.isEmpty()) return emptyList()

        // Fetch matched photos in batches of 10 (Firestore limitation for 'in' queries).
        // Match on the document ID, because photo documents don't store an "id" field.
        val photos = mutableListOf<Photo>()
        matchedPhotoIds.chunked(10).forEach { batch ->
            val photosSnapshot = firestore.collection("events")
                .document(eventId)
                .collection("photos")
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .await()

            photos.addAll(
                photosSnapshot.documents.mapNotNull { it.toObject(Photo::class.java)?.copy(id = it.id) }
            )
        }

        return photos
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun submitPrintRequest(
        eventId: String,
        guestId: String,
        photoIds: List<String>,
        quantityPerPhoto: Int,
        pricePerPrint: Double // ignored on purpose: the studio's price from the event is used
    ) {
        ensureSignedIn()
        if (photoIds.isEmpty() || quantityPerPhoto < 1) {
            throw Exception("Select at least one photo and a quantity of 1 or more.")
        }

        // Never trust the price sent from the phone. Read the real settings from the event.
        val event = getEvent(eventId)
        if (event.status != "ACTIVE") throw Exception("This event has ended. Contact studio.")
        if (!event.allowPrint) throw Exception("Print requests are turned off for this event.")

        val uid = auth.currentUser?.uid ?: throw Exception("Please rejoin the event and try again.")
        val requestId = UUID.randomUUID().toString()
        val totalQuantity = photoIds.size * quantityPerPhoto
        val totalPrice = totalQuantity * event.pricePerPrint

        val request = PrintRequest(
            id = requestId,
            eventId = eventId,
            guestId = uid,
            photoIds = photoIds,
            totalQuantity = totalQuantity,
            totalPrice = totalPrice
        )

        firestore.collection("events")
            .document(eventId)
            .collection("printRequests")
            .document(requestId)
            .set(request)
            .await()
    }
}