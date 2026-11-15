package com.appybits.eventsnap.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_items")
data class UploadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val eventId: String,
    val photographerId: String,
    val isCommon: Boolean,
    val status: UploadStatus,
    val progress: Int = 0
)