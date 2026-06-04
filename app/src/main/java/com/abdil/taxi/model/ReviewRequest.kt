package com.abdil.taxi.model

data class ReviewRequest(
    val rideId: Long,
    val clientId: Long,
    val rating: Int,
    val comment: String
)