package com.abdil.taxi.model

data class MessageResponse(
    val id: Long,
    val rideId: Long,
    val senderId: Long,
    val senderType: String,
    val receiverId: Long,
    val content: String,
    val messageType: String? = "TEXT",
    val mediaUrl: String? = null,
    val reaction: String? = null,
    val isRead: Boolean = false,
    val createdAt: String
)