package com.abdil.taxi.model

data class PaymentResponse(
    val transactionId: String,
    val status: String,
    val amount: Double,
    val paymentMethod: String,
    val message: String
)