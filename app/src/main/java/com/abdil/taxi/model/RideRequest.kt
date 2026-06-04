package com.abdil.taxi.model

data class RideRequest(
    val userId: Long,
    val clientName: String,
    val clientPhone: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val distance: Double,
    val rideType: String = "STANDARD",
    val femaleOnly: Boolean = false
)