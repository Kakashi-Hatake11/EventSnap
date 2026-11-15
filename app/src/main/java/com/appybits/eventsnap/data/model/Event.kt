package com.appybits.eventsnap.data.model

data class Event(
    val id: String = "",
    val name: String = "",
    val clientName: String = "",
    val venue: String = "",
    val date: Long = 0L,
    val adminId: String = "",
    val allowDownload: Boolean = true,
    val allowPrint: Boolean = false,
    val pricePerPrint: Double = 0.0,
    val qrCodeValue: String = "",
    val pin: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val coverImageUrl: String = ""
)
