package com.appybits.eventsnap.data.model

data class PrintRequest(
    val id: String = "",
    val eventId: String = "",
    val guestId: String = "",
    val photoIds: List<String> = emptyList(),
    val totalQuantity: Int = 0,
    val totalPrice: Double = 0.0,
    val requestedAt: Long = System.currentTimeMillis()
)
