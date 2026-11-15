package com.appybits.eventsnap.data.model

data class Selfie(
    val id: String = "",
    val guestId: String = "",
    val storagePath: String = "",
    val matchedPhotoIds: List<String> = emptyList(),
    val uploadedAt: Long = System.currentTimeMillis()
)
