package com.abdil.taxi.model

data class AuthResponse(
    val token: String?,
    val message: String?,
    val success: Boolean,
    val user: User?
)

data class User(
    val id: Long,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String
)