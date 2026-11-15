package com.appybits.eventsnap.data.repository

import android.net.Uri
import com.appybits.eventsnap.data.model.Event
import com.appybits.eventsnap.data.model.Photo
import com.appybits.eventsnap.data.model.Photographer
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val eventsCollection = firestore.collection("events")

    fun getEvents(adminId: String): Flow<List<Event>> = callbackFlow {
        val listener = eventsCollection.whereEqualTo("adminId", adminId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { it.toObject(Event::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(events)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createEventWithId(eventId: String, event: Event): Result<String> {
        return try {
            val document = eventsCollection.document(eventId)
            val snap = document.get().await()
            if (snap.exists()) {
                return Result.failure(Exception("Event ID already exists. Try a different one."))
            }
            if (event.adminId.isBlank()) {
                return Result.failure(Exception("Please sign in again."))
            }
            val newEvent = event.copy(id = eventId)

            // The event and the studio's event counter are written together. The security rules
            // only accept the event if the counter goes up by exactly 1 within the plan's allowance.
            val account = firestore.collection("studioAccounts").document(event.adminId)
            firestore.batch().apply {
                set(document, newEvent)
                update(account, "eventsUsed", FieldValue.increment(1))
            }.commit().await()
            Result.success(eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendly(e))
        }
    }

    suspend fun updateEvent(event: Event): Result<Unit> {
        return try {
            eventsCollection.document(event.id).set(event).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendly(e))
        }
    }

    suspend fun uploadEventCoverImage(eventId: String, uri: Uri): Result<String> {
        return try {
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val ref = storage.reference.child("events/$eventId/cover/$fileName")
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEvent(eventId: String): Result<Event> {
        return try {
            val doc = eventsCollection.document(eventId).get().await()
            val event = doc.toObject(Event::class.java)?.copy(id = doc.id)
            if (event != null) {
                Result.success(event)
            } else {
                Result.failure(Exception("Event not found"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes the event and everything under it: photographers, photos, guests (and their selfies),
     * print requests, and the files in Storage. Files go first because the Storage rules look up the
     * event document to check ownership; the event document itself is removed last.
     */
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            // Best effort: a failure here shouldn't stop the database cleanup.
            try {
                deleteStorageFolder(storage.reference.child("events/$eventId"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }

            val eventRef = eventsCollection.document(eventId)

            // Selfies live one level deeper, under each guest.
            val guests = eventRef.collection("guests").get().await()
            for (guest in guests.documents) {
                deleteCollection(guest.reference.collection("selfies"))
            }
            for (sub in listOf("photographers", "photos", "guests", "printRequests")) {
                deleteCollection(eventRef.collection(sub))
            }
            eventRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendly(e))
        }
    }

    /** Deletes every document in a collection in batches (Firestore allows 500 writes per batch). */
    private suspend fun deleteCollection(ref: CollectionReference) {
        while (true) {
            val snap = ref.limit(400).get().await()
            if (snap.isEmpty) return
            val batch = firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Storage has no "delete folder", so list and delete level by level. */
    private suspend fun deleteStorageFolder(ref: StorageReference) {
        val listing = ref.listAll().await()
        listing.items.forEach { it.delete().await() }
        listing.prefixes.forEach { deleteStorageFolder(it) }
    }

    suspend fun addPhotographer(eventId: String, photographer: Photographer): Result<Unit> {
        return try {
            val doc = eventsCollection.document(eventId).collection("photographers").document()
            val newPhotographer = photographer.copy(id = doc.id, eventId = eventId)
            doc.set(newPhotographer).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendly(e))
        }
    }

    fun getPhotographers(eventId: String): Flow<List<Photographer>> = callbackFlow {
        val listener = eventsCollection.document(eventId).collection("photographers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val photographers = snapshot?.documents?.mapNotNull { it.toObject(Photographer::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(photographers)
            }
        awaitClose { listener.remove() }
    }

    fun getAnalytics(eventId: String): Flow<EventAnalytics> = callbackFlow {
        // Real-time listener for photos, guests, prints
        val photosRef = eventsCollection.document(eventId).collection("photos")
        val guestsRef = eventsCollection.document(eventId).collection("guests")
        // Must match the collection name GuestRepository writes to ("printRequests").
        val printsRef = eventsCollection.document(eventId).collection("printRequests")

        var photosCount = 0
        var guestsCount = 0
        var printsCount = 0

        fun updateAnalytics() {
            trySend(EventAnalytics(photosCount, guestsCount, printsCount))
        }

        val pListener = photosRef.addSnapshotListener { snap, _ ->
            photosCount = snap?.size() ?: 0
            updateAnalytics()
        }
        val gListener = guestsRef.addSnapshotListener { snap, _ ->
            guestsCount = snap?.size() ?: 0
            updateAnalytics()
        }
        val prListener = printsRef.addSnapshotListener { snap, _ ->
            printsCount = snap?.size() ?: 0
            updateAnalytics()
        }

        awaitClose {
            pListener.remove()
            gListener.remove()
            prListener.remove()
        }
    }

    fun getAllPhotos(eventId: String): Flow<List<Photo>> = callbackFlow {
        val listener = eventsCollection.document(eventId).collection("photos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                // The document ID is the photo's ID (UploadWorker doesn't store an "id" field).
                val photos = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Photo::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(photos)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Deletes photos one by one and reports how many worked. The security rules decide who may delete
     * what (the studio: anything; a photographer: only photos they uploaded), so some deletes can be
     * denied. The file in Storage is removed only AFTER its database record is gone, so a denied
     * delete can never leave a record pointing at a missing file.
     */
    suspend fun deletePhotos(eventId: String, photos: List<Photo>): Result<DeletePhotosOutcome> {
        return try {
            val photosRef = eventsCollection.document(eventId).collection("photos")
            var deleted = 0
            var failed = 0

            for (photo in photos) {
                try {
                    photosRef.document(photo.id).delete().await()
                    deleted++

                    if (photo.downloadUrl.isNotEmpty()) {
                        try {
                            storage.getReferenceFromUrl(photo.downloadUrl).delete().await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Ignore if the file is already gone or not ours to delete.
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed++
                }
            }
            Result.success(DeletePhotosOutcome(deleted, failed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendly(e))
        }
    }

    /** Turns Firestore's technical "permission denied" into something a studio can act on. */
    private fun friendly(e: Exception): Exception =
        if (e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) {
            Exception(
                "This isn't allowed on your plan right now. It may be inactive or expired, " +
                        "or you may have used all your events. Contact support.",
                e
            )
        } else {
            e
        }
}

data class DeletePhotosOutcome(val deleted: Int, val failed: Int)

data class EventAnalytics(
    val photoCount: Int = 0,
    val guestCount: Int = 0,
    val printRequestsCount: Int = 0
)