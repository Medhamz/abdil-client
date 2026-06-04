package com.abdil.taxi.model

data class PriceRequest(
    val pickupAddress: String,
    val destinationAddress: String,
    val distance: Double,
    val rideType: String = "STANDARD"
)