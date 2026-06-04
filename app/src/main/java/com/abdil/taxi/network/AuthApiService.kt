package com.abdil.taxi.network

import com.abdil.taxi.model.AuthRequest
import com.abdil.taxi.model.AuthResponse
import com.abdil.taxi.model.RegisterRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/auth/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>

    @POST("api/auth/login")
    fun login(@Body request: AuthRequest): Call<AuthResponse>
}