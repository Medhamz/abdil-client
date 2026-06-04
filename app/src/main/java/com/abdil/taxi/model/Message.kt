package com.abdil.taxi.model

import java.util.Date

data class Message(
    val id: Long = 0,
    val rideId: Long,
    val senderId: Long,
    val senderType: String, // CLIENT, DRIVER
    val receiverId: Long,
    val content: String,
    val messageType: String = "TEXT", // TEXT, VOICE, IMAGE
    val mediaUrl: String? = null,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val createdAt: Date = Date()
)