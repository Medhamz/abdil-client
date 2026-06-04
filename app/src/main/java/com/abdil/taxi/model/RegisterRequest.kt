package com.abdil.taxi.model

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val phone: String,
    val password: String
)