package com.abdil.taxi.model

data class PriceResponse(
    val distance: Double,
    val duration: String,
    val estimatedPrice: Double,
    val basePrice: Double,
    val multiplier: Double,
    val breakdown: String
)