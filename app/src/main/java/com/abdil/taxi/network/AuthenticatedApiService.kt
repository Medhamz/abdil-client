package com.abdil.taxi.network

import com.abdil.taxi.model.PriceRequest
import com.abdil.taxi.model.PriceResponse
import com.abdil.taxi.model.RideRequest
import com.abdil.taxi.model.RideResponse
import retrofit2.Call
import retrofit2.http.*

interface AuthenticatedApiService {
    @POST("api/taxi/price")
    fun calculatePrice(@Body request: PriceRequest): Call<PriceResponse>

    @POST("api/taxi/ride")
    fun createRide(@Body request: RideRequest): Call<RideResponse>

    @GET("api/taxi/rides")
    fun getAllRides(): Call<List<RideResponse>>
}