package com.appybits.eventsnap.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Photo(
    val id: String = "",
    val eventId: String = "",
    val storagePath: String = "",

    @get:PropertyName("url")
    @set:PropertyName("url")
    var downloadUrl: String = "",

    val photographerId: String = "",

    @get:PropertyName("isCommon")
    @set:PropertyName("isCommon")
    var isCommon: Boolean = false,

    @get:PropertyName("timestamp")
    @set:PropertyName("timestamp")
    var uploadedAtTimestamp: Timestamp = Timestamp.now()
) {
    // Keep this so any existing code using `uploadedAt: Long` still works
    val uploadedAt: Long
        get() = uploadedAtTimestamp.toDate().time
}