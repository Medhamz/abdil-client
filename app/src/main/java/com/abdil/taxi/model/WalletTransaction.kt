package com.abdil.taxi.model

data class WalletTransaction(
    val id: Long,
    val walletId: Long,
    val amount: Double,
    val type: String, // CREDIT, DEBIT, REFUND
    val status: String, // PENDING, COMPLETED, FAILED
    val reference: String,
    val description: String,
    val createdAt: String
)