package com.appybits.eventsnap.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.appybits.eventsnap.data.local.UploadDao
import com.appybits.eventsnap.data.local.UploadStatus
import com.appybits.eventsnap.utils.ImageCompressor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadDao: UploadDao,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        var successCount = 0
        while (true) {
            val pendingUpload = uploadDao.getNextPendingUpload() ?: break

            uploadDao.updateStatus(pendingUpload.id, UploadStatus.UPLOADING)

            var compressedFile: File? = null
            try {
                // The photo picker already granted persistable access when the photo was chosen,
                // so there is nothing to request again here.
                val contentUri = Uri.parse(pendingUpload.uri)

                compressedFile = ImageCompressor.compressImage(context, contentUri)
                if (compressedFile == null) {
                    uploadDao.updateStatus(pendingUpload.id, UploadStatus.FAILED)
                    // Continue with the next upload instead of failing the whole batch
                    continue
                }

                val fileName = "${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child("events/${pendingUpload.eventId}/photos/$fileName")

                // Explicit content type: the Storage rules only accept images, and a temporary
                // file's type can't always be guessed.
                // "uploaderUid" records who uploaded the file; the Storage rules use it so a
                // photographer can delete only their own photos.
                val uploaderUid = auth.currentUser?.uid
                    ?: throw IllegalStateException("Not signed in")
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCustomMetadata("uploaderUid", uploaderUid)
                    .build()
                storageRef.putFile(Uri.fromFile(compressedFile), metadata).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val photoDoc = hashMapOf(
                    "url" to downloadUrl,
                    "photographerId" to pendingUpload.photographerId,
                    "uploaderUid" to uploaderUid,
                    "isCommon" to pendingUpload.isCommon,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )

                firestore.collection("events").document(pendingUpload.eventId)
                    .collection("photos").add(photoDoc).await()

                uploadDao.updateStatus(pendingUpload.id, UploadStatus.COMPLETED)
                successCount++

            } catch (e: CancellationException) {
                // WorkManager stopped us (lost network, app update, ...). Put the photo back in the
                // queue instead of marking it failed, then let the cancellation continue.
                withContext(NonCancellable) {
                    uploadDao.updateStatus(pendingUpload.id, UploadStatus.PENDING)
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                uploadDao.updateStatus(pendingUpload.id, UploadStatus.FAILED)
            } finally {
                // Clean up the temporary file whether the upload worked or not.
                compressedFile?.delete()
            }
        }

        if (successCount > 0) {
            com.appybits.eventsnap.utils.NotificationHelper.showUploadCompleteNotification(context, successCount)
        }

        return Result.success()
    }
}