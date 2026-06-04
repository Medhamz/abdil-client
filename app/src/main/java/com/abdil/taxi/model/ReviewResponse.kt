package com.abdil.taxi.model

data class ReviewResponse(
    val success: Boolean,
    val message: String? = null,
    val review: ReviewData? = null
)

data class ReviewData(
    val id: Long,
    val rideId: Long,
    val clientId: Long,
    val driverId: Long,
    val rating: Int,
    val comment: String,
    val createdAt: String
)