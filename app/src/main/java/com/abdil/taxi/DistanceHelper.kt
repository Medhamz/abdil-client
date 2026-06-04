package com.abdil.taxi

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class DistanceHelper(private val context: Context) {

    private val API_KEY = "AIzaSyC0sT4Ps3Er58XbwYXUnSfnW8c-ZWQodIc"
    private val TAG = "DistanceHelper"

    suspend fun calculateDistance(pickupAddress: String, destinationAddress: String): Double {
        return withContext(Dispatchers.IO) {
            try {
                // Encoder les adresses pour l'URL
                val origin = java.net.URLEncoder.encode(pickupAddress, "UTF-8")
                val dest = java.net.URLEncoder.encode(destinationAddress, "UTF-8")

                val url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=$origin&destinations=$dest&units=metric&language=fr&key=$API_KEY"

                Log.d(TAG, "URL: $url")

                val response = URL(url).readText()
                Log.d(TAG, "Réponse: $response")

                val json = JSONObject(response)
                val status = json.getString("status")

                if (status == "OK") {
                    val rows = json.getJSONArray("rows")
                    if (rows.length() > 0) {
                        val elements = rows.getJSONObject(0).getJSONArray("elements")
                        if (elements.length() > 0) {
                            val element = elements.getJSONObject(0)
                            val elementStatus = element.getString("status")

                            if (elementStatus == "OK") {
                                val distanceJson = element.getJSONObject("distance")
                                val distanceInMeters = distanceJson.getDouble("value")
                                return@withContext distanceInMeters / 1000.0 // Convertir en km
                            } else {
                                Log.e(TAG, "Element status: $elementStatus")
                                return@withContext 0.0
                            }
                        }
                    }
                }
                Log.e(TAG, "Status API: $status")
                return@withContext 0.0

            } catch (e: Exception) {
                Log.e(TAG, "Erreur: ${e.message}")
                e.printStackTrace()
                return@withContext 0.0
            }
        }
    }
}