package com.abdil.taxi.model

data class PaymentRequest(
    var rideId: Long,
    var userId: Long,
    var amount: Double,
    var paymentMethod: String,
    var phoneNumber: String? = null,
    var cardToken: String? = null,
    var customerName: String,
    var customerEmail: String
)