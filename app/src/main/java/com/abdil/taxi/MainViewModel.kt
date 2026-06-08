package com.abdil.taxi

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdil.taxi.model.PriceRequest
import com.abdil.taxi.model.PriceResponse
import com.abdil.taxi.model.RideRequest
import com.abdil.taxi.model.RideResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {

    private val BASE_URL = "http://192.168.11.105:8080/"

    private val _estimatedPrice = MutableLiveData<PriceResponse?>()
    val estimatedPrice: LiveData<PriceResponse?> = _estimatedPrice

    private val _rideResult = MutableLiveData<RideResponse?>()
    val rideResult: LiveData<RideResponse?> = _rideResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    var userId: Long = -1
    var currentRideType: String = "STANDARD"

    fun calculatePrice(pickupAddress: String, destinationAddress: String, distance: Double, rideType: String) {
        if (distance <= 0) {
            _errorMessage.value = "La distance doit être supérieure à 0 km"
            return
        }

        _isLoading.value = true
        _errorMessage.value = "Calcul en cours..."

        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${BASE_URL}api/taxi/price")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val json = JSONObject()
                json.put("pickupAddress", pickupAddress)
                json.put("destinationAddress", destinationAddress)
                json.put("distance", distance)
                json.put("rideType", rideType)

                val outputStream: OutputStream = connection.outputStream
                outputStream.write(json.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val gson = com.google.gson.Gson()
                    val priceResponse = gson.fromJson(response, PriceResponse::class.java)
                    withContext(Dispatchers.Main) {
                        _estimatedPrice.value = priceResponse
                        _errorMessage.value = null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Erreur HTTP: $responseCode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erreur: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                connection?.disconnect()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // ✅ MÉTHODE AVEC PASSAGE PAR MOSQUÉE
    fun calculatePriceWithMosque(pickupAddress: String, destinationAddress: String, distance: Double, rideType: String, passByMosque: Boolean) {
        if (distance <= 0) {
            _errorMessage.value = "La distance doit être supérieure à 0 km"
            return
        }

        _isLoading.value = true
        _errorMessage.value = "Calcul en cours..."

        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${BASE_URL}api/taxi/price")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val json = JSONObject()
                json.put("pickupAddress", pickupAddress)
                json.put("destinationAddress", destinationAddress)
                json.put("distance", distance)
                json.put("rideType", rideType)
                json.put("passByMosque", passByMosque)

                val outputStream: OutputStream = connection.outputStream
                outputStream.write(json.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val gson = com.google.gson.Gson()
                    val priceResponse = gson.fromJson(response, PriceResponse::class.java)
                    withContext(Dispatchers.Main) {
                        _estimatedPrice.value = priceResponse
                        _errorMessage.value = null
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Erreur HTTP: $responseCode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erreur: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                connection?.disconnect()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // ✅ BOOK RIDE COMPLÈTE AVEC PAYMENT METHOD
    fun bookRide(clientName: String, clientPhone: String, pickupAddress: String,
                 destinationAddress: String, distance: Double, rideType: String,
                 femaleOnly: Boolean = false, passByMosque: Boolean = false,
                 paymentMethod: String = "CASH", paymentPhone: String = "") {
        if (clientName.isBlank() || clientPhone.isBlank() || pickupAddress.isBlank() || destinationAddress.isBlank()) {
            _errorMessage.value = "Veuillez remplir tous les champs"
            return
        }

        if (distance <= 0) {
            _errorMessage.value = "Distance invalide"
            return
        }

        _isLoading.value = true
        _errorMessage.value = "Réservation en cours..."

        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${BASE_URL}api/taxi/ride")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val json = JSONObject()
                json.put("userId", userId)
                json.put("clientName", clientName)
                json.put("clientPhone", clientPhone)
                json.put("pickupAddress", pickupAddress)
                json.put("destinationAddress", destinationAddress)
                json.put("distance", distance)
                json.put("rideType", rideType)
                json.put("femaleOnly", femaleOnly)
                json.put("passByMosque", passByMosque)
                json.put("paymentMethod", paymentMethod)
                json.put("paymentPhone", paymentPhone)

                val outputStream: OutputStream = connection.outputStream
                outputStream.write(json.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    val gson = com.google.gson.Gson()
                    val rideResponse = gson.fromJson(response, RideResponse::class.java)
                    withContext(Dispatchers.Main) {
                        _rideResult.value = rideResponse
                        _errorMessage.value = "✅ Course réservée avec succès!"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Erreur HTTP: $responseCode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erreur: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                connection?.disconnect()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearEstimatedPrice() {
        _estimatedPrice.value = null
    }
}