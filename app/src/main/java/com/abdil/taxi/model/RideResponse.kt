package com.abdil.taxi.model

data class RideResponse(
    val id: Long,
    val userId: Long,
    val driverId: Long? = null,
    val driverName: String? = null,
    val pickupAddress: String,
    val destinationAddress: String,
    val status: String,
    val estimatedPrice: Double,
    val distance: Double,
    val createdAt: String,
    val cancellationReason: String? = null,
    // ✅ AJOUTER CES DEUX CHAMPS POUR LA PAUSE
    val driverIsOnPause: Boolean? = false,
    val driverPauseReason: String? = null,
    val paymentMethod: String? = null
)