package com.appybits.eventsnap.data.model

data class Guest(
    val id: String = "",
    val eventId: String = "",
    val joinedAt: Long = System.currentTimeMillis()
)
