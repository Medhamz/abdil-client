package com.abdil.taxi.model

data class ScheduledRide(
    val id: Long = 0,
    val userId: Long,
    val clientName: String,
    val clientPhone: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val distance: Double = 0.0,
    val estimatedPrice: Double = 0.0,
    val rideType: String = "STANDARD",
    val scheduledDateTime: String,
    val status: String = "PENDING",
    val createdAt: String = ""
)