package com.abdil.taxi.model

data class AdCampaign(
    var id: Long = 0,
    var clientId: Long = 0,
    var clientName: String = "",
    var clientPhone: String = "",
    var productName: String = "",
    var description: String = "",
    var duration: String = "", // "1 MONTH", "6 MONTHS", "1 YEAR"
    var price: Double = 0.0,
    var paymentMethod: String = "", // "WALLET", "CASH"
    var status: String = "PENDING",
    var adminNotes: String? = null,
    var createdAt: String? = null,
    var paidAt: String? = null,
    var validatedAt: String? = null,
    var receiptUrl: String? = null
)