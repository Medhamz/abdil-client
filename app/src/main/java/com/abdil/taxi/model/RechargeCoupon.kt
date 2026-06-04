package com.abdil.taxi.model

data class RechargeCoupon(
    val id: Long,
    val code: String,
    val amount: Double,
    val status: String, // ACTIVE, USED, EXPIRED
    val createdBy: String? = null,
    val usedByUserId: Long? = null,
    val usedAt: String? = null,
    val expiresAt: String? = null,
    val createdAt: String
)