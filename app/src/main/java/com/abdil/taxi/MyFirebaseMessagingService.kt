package com.abdil.taxi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abdil.taxi.network.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_Client"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "📱 Nouveau token FCM: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "========================================")
        Log.d(TAG, "📨 MESSAGE REÇU CLIENT!")
        Log.d(TAG, "Title: ${remoteMessage.notification?.title}")
        Log.d(TAG, "Body: ${remoteMessage.notification?.body}")
        Log.d(TAG, "Data: ${remoteMessage.data}")
        Log.d(TAG, "========================================")

        // Récupérer les données
        val rideId = remoteMessage.data["rideId"]?.toLongOrNull() ?: -1
        val senderId = remoteMessage.data["senderId"]?.toLongOrNull() ?: -1
        val senderName = remoteMessage.data["senderName"] ?: "Chauffeur"

        val title = remoteMessage.notification?.title ?: "Abdil Taxi"
        val body = remoteMessage.notification?.body ?: "Nouvelle notification"

        showNotification(title, body, rideId, senderId, senderName)
    }

    private fun sendTokenToServer(token: String) {
        val tokenManager = TokenManager(this)
        val userId = tokenManager.getUserId()
        val userType = "CLIENT"
        val deviceId = UUID.randomUUID().toString()

        Log.d(TAG, "=== ENVOI TOKEN AU SERVEUR ===")
        Log.d(TAG, "userId: $userId")
        Log.d(TAG, "userType: $userType")

        if (userId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val call = RetrofitClient.apiService.registerToken(userId, token, deviceId, userType)
                    val response = call.execute()
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Token enregistré sur le serveur")
                    } else {
                        Log.e(TAG, "❌ Erreur enregistrement token: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception: ${e.message}")
                }
            }
        } else {
            Log.e(TAG, "❌ userId = -1, token non envoyé")
        }
    }

    private fun showNotification(title: String, body: String, rideId: Long, senderId: Long, senderName: String) {
        val channelId = "abdil_taxi_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Abdil Taxi Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications des courses et messages"
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("RIDE_ID", rideId)
            putExtra("DRIVER_ID", senderId)
            putExtra("CLIENT_ID", TokenManager(this@MyFirebaseMessagingService).getUserId())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, rideId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(rideId.toInt(), notification)
        Log.d(TAG, "✅ Notification affichée")
    }
}