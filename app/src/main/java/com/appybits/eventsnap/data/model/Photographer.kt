package com.appybits.eventsnap.data.model

data class Photographer(
    val id: String = "",
    val username: String = "",
    val pin: String = "",
    val eventId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
