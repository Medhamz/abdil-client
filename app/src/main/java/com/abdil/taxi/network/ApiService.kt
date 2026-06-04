package com.abdil.taxi.network

import com.abdil.taxi.model.*
import retrofit2.Call
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody

// Requête pour l'envoi de message
data class SendMessageRequest(
    val rideId: Long,
    val senderId: Long,
    val senderType: String,
    val receiverId: Long,
    val content: String,
    val messageType: String = "TEXT",
    val mediaUrl: String = ""
)

interface ApiService {

    @GET("api/taxi/health")
    fun healthCheck(): Call<String>

    @GET("api/taxi/rides")
    fun getAllRides(): Call<List<RideResponse>>

    @POST("api/taxi/price")
    fun calculatePrice(@Body request: PriceRequest): Call<PriceResponse>

    @POST("api/taxi/ride")
    fun createRide(@Body request: RideRequest): Call<RideResponse>

    @GET("api/taxi/client/active/{clientId}")
    fun getActiveRideForClient(@Path("clientId") clientId: Long): Call<RideResponse>

    @POST("api/notifications/register")
    fun registerToken(
        @Query("userId") userId: Long,
        @Query("token") token: String,
        @Query("deviceId") deviceId: String,
        @Query("userType") userType: String
    ): Call<Void>

    @GET("api/taxi/client/{clientId}/rides")
    fun getClientRides(@Path("clientId") clientId: Long): Call<List<RideResponse>>

    @DELETE("api/taxi/client/ride/{rideId}")
    fun deleteClientRide(@Path("rideId") rideId: Long): Call<Void>

    @DELETE("api/taxi/client/rides/batch")
    fun deleteMultipleClientRides(@Body rideIds: List<Long>): Call<String>

    @GET("api/driver/location/{driverId}")
    fun getDriverLocation(@Path("driverId") driverId: Long): Call<Map<String, Double>>

    @PUT("api/taxi/ride/{rideId}/cancel")
    fun cancelRideByClient(@Path("rideId") rideId: Long,
                           @Query("clientId") clientId: Long,
                           @Query("reason") reason: String?): Call<RideResponse>

    // NOTATION DES COURSES
    @POST("api/reviews/add")
    fun addReview(@Body request: ReviewRequest): Call<ReviewResponse>

    @GET("api/reviews/check/{rideId}")
    fun checkReviewExists(@Path("rideId") rideId: Long): Call<Map<String, Boolean>>

    // CHAT - Utiliser Map pour la réponse (plus flexible)
    @POST("api/messages/send")
    fun sendMessage(@Body request: SendMessageRequest): Call<Map<String, Any>>

    @GET("api/messages/ride/{rideId}")
    fun getMessages(@Path("rideId") rideId: Long): Call<List<Map<String, Any>>>

    // AUDIO
    @Multipart
    @POST("api/audio/upload")
    fun uploadAudio(@Part file: MultipartBody.Part): Call<Map<String, String>>

    @GET("api/audio/download/{fileName}")
    fun downloadAudio(@Path("fileName") fileName: String): Call<ResponseBody>

    // AGORA
    @GET("api/agora/token/{channelName}/{uid}")
    fun generateToken(@Path("channelName") channelName: String, @Path("uid") uid: Int): Call<Map<String, String>>

    // AGORA - Génération token
    @GET("api/agora/token/{channelName}/{uid}")
    fun getAgoraToken(@Path("channelName") channelName: String, @Path("uid") uid: Int): Call<Map<String, String>>

    @PUT("api/messages/read/{rideId}/{messageId}/{userId}")
    fun markMessageAsRead(@Path("rideId") rideId: Long, @Path("messageId") messageId: Long, @Path("userId") userId: Long): Call<Void>

    @PUT("api/messages/readAll/{rideId}/{userId}")
    fun markAllMessagesAsRead(@Path("rideId") rideId: Long, @Path("userId") userId: Long): Call<Void>

    @PUT("api/messages/reaction/{messageId}/{userId}")
    fun addReaction(@Path("messageId") messageId: Long,
                    @Path("userId") userId: Long,
                    @Query("reaction") reaction: String): Call<Void>

    @Multipart
    @POST("api/images/upload")
    fun uploadImage(@Part file: MultipartBody.Part): Call<Map<String, String>>

    // Live Sharing endpoints
    @POST("/api/live/create/{rideId}")
    fun createLiveShare(@Path("rideId") rideId: Long): Call<Map<String, String>>

    @POST("/api/live/update/{shareCode}")
    fun updateLiveLocation(
        @Path("shareCode") shareCode: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): Call<Void>

    @GET("/api/live/viewers/{shareCode}")
    fun getLiveShareViewers(@Path("shareCode") shareCode: String): Call<Map<String, Any>>

    @DELETE("/api/live/stop/{shareCode}")
    fun stopLiveShare(@Path("shareCode") shareCode: String): Call<Void>

    @GET("api/version/client")
    fun getClientVersion(): Call<Map<String, Any>>

    // Scheduled Rides
    @POST("api/scheduled/create")
    fun createScheduledRide(@Body request: ScheduledRide): Call<ScheduledRide>

    @GET("api/scheduled/user/{userId}/upcoming")
    fun getUpcomingScheduledRides(@Path("userId") userId: Long): Call<List<ScheduledRide>>

    @DELETE("api/scheduled/{id}")
    fun cancelScheduledRide(@Path("id") id: Long): Call<Void>

    @POST("api/payments/initiate")
    fun initiatePayment(@Body request: PaymentRequest): Call<PaymentResponse>

    @POST("api/payments/verify/{transactionId}")
    fun verifyPayment(@Path("transactionId") transactionId: String): Call<PaymentResponse>

    @GET("api/payments/methods")
    fun getPaymentMethods(): Call<List<String>>

    // ==================== WALLET (PORTE-MONNAIE) ====================

    @GET("api/wallet/balance/{userId}")
    fun getWalletBalance(@Path("userId") userId: Long): Call<Map<String, Any>>

    @POST("api/wallet/recharge")
    fun rechargeWallet(
        @Query("userId") userId: Long,
        @Query("amount") amount: Double,
        @Query("paymentMethod") paymentMethod: String,
        @Query("phoneNumber") phoneNumber: String?
    ): Call<WalletTransaction>

    @POST("api/wallet/pay")
    fun payWithWallet(
        @Query("userId") userId: Long,
        @Query("amount") amount: Double,
        @Query("rideId") rideId: String
    ): Call<Map<String, Any>>

    @GET("api/wallet/transactions/{userId}")
    fun getWalletTransactions(@Path("userId") userId: Long): Call<List<WalletTransaction>>

    @POST("api/wallet/coupons/redeem")
    fun redeemCoupon(
        @Query("code") code: String,
        @Query("userId") userId: Long
    ): Call<Map<String, Any>>

    @GET("api/wallet/coupons/all")
    fun getAllCoupons(): Call<List<RechargeCoupon>>

    // ==================== QR CODE ====================
    @GET("api/wallet/qrcode/{userId}")
    fun generateQRCode(
        @Path("userId") userId: Long,
        @Query("amount") amount: Double,
        @Query("rideId") rideId: Long
    ): Call<Map<String, String>>

    @POST("api/wallet/qrcode/scan/{token}")
    fun scanQRCode(@Path("token") token: String): Call<Map<String, Any>>

    // ==================== CRYPTO ====================
    @POST("api/crypto/generate-address")
    fun generateCryptoAddress(
        @Query("rideId") rideId: Long,
        @Query("amount") amount: Double,
        @Query("currency") currency: String
    ): Call<Map<String, String>>

    @GET("api/crypto/check-status")
    fun checkCryptoStatus(
        @Query("transactionId") transactionId: String,
        @Query("amount") amount: Double
    ): Call<Map<String, Any>>

    // ==================== PAIEMENT PAR LIEN ====================
    @POST("api/payment-link/generate")
    fun generatePaymentLink(
        @Query("rideId") rideId: Long,
        @Query("clientId") clientId: Long,
        @Query("amount") amount: Double
    ): Call<Map<String, String>>

    // ==================== PAIEMENT NFC ====================
    @GET("api/nfc-payment/generate-token")
    fun generateNfcToken(): Call<Map<String, String>>

    @POST("api/nfc-payment/pay")
    fun processNfcPayment(
        @Query("clientId") clientId: Long,
        @Query("driverId") driverId: Long,
        @Query("rideId") rideId: Long,
        @Query("amount") amount: Double,
        @Query("nfcToken") nfcToken: String
    ): Call<Map<String, Any>>

    @POST("api/nfc-payment/initiate")
    fun initiateNfcPayment(
        @Query("clientId") clientId: Long,
        @Query("rideId") rideId: Long,
        @Query("amount") amount: Double
    ): Call<Map<String, String>>

    // ==================== TAXI PUB (ADVERTISING) ====================
    @POST("api/advertising/create")   // ✅ modifié : /submit → /create
    fun createAd(@Body ad: AdCampaign): Call<Map<String, Any>>

    @POST("api/advertising/pay/{campaignId}")
    fun payForAd(
        @Path("campaignId") campaignId: Long,
        @Query("clientId") clientId: Long,   // ✅ modifié : userId → clientId
        @Query("paymentMethod") paymentMethod: String
    ): Call<Map<String, Any>>

}