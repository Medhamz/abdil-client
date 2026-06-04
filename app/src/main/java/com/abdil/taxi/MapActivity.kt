package com.abdil.taxi

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.abdil.taxi.network.RetrofitClient
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var tvLocationInfo: TextView
    private lateinit var tvDriverTracking: TextView
    private lateinit var layoutTrackingInfo: android.view.View
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Variables pour le suivi du chauffeur
    private var driverMarker: Marker? = null
    private var activeDriverId: Long = -1
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var tokenManager: TokenManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        tvLocationInfo = findViewById(R.id.tvLocationInfo)
        tvDriverTracking = findViewById(R.id.tvDriverTracking)
        layoutTrackingInfo = findViewById(R.id.layoutTrackingInfo)

        // Récupérer l'ID du chauffeur depuis l'intent
        activeDriverId = intent.getLongExtra("DRIVER_ID", -1)

        // Initialiser TokenManager
        tokenManager = TokenManager(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Si un chauffeur est assigné, afficher la section de suivi
        if (activeDriverId != -1L) {
            layoutTrackingInfo.visibility = android.view.View.VISIBLE
            tvDriverTracking.text = "🔄 Suivi du chauffeur en cours..."
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Activer les contrôles
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        // Vérifier les permissions de localisation
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            getCurrentLocationAndShow()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        // Démarrer le suivi du chauffeur si un ID est fourni
        if (activeDriverId != -1L) {
            startDriverTracking()
        }
    }

    private fun getCurrentLocationAndShow() {
        runBlocking {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MapActivity)
                if (ActivityCompat.checkSelfPermission(
                        this@MapActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Vous êtes ici"))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                        tvLocationInfo.text = "Position: ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}"
                    } else {
                        tvLocationInfo.text = "Position non disponible"
                        showDefaultLocation()
                    }
                }
            } catch (e: Exception) {
                tvLocationInfo.text = "Erreur: ${e.message}"
                showDefaultLocation()
            }
        }
    }

    private fun showDefaultLocation() {
        val casablanca = LatLng(33.5731, -7.5898)
        mMap.addMarker(MarkerOptions().position(casablanca).title("Casablanca, Maroc"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(casablanca, 12f))
        tvLocationInfo.text = "Position par défaut: Casablanca"
    }

    private fun startDriverTracking() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadDriverLocation()
                handler.postDelayed(this, 5000) // Mise à jour toutes les 5 secondes
            }
        }
        handler.post(refreshRunnable!!)

        // Afficher un message d'information
        tvDriverTracking.text = "📍 Suivi du chauffeur actif (mise à jour toutes les 5 secondes)"
    }

    private fun loadDriverLocation() {
        if (activeDriverId == -1L) return

        RetrofitClient.apiService.getDriverLocation(activeDriverId).enqueue(object : Callback<Map<String, Double>> {
            override fun onResponse(call: Call<Map<String, Double>>, response: Response<Map<String, Double>>) {
                if (response.isSuccessful && response.body() != null) {
                    val location = response.body()!!
                    val latitude = location["latitude"] ?: 0.0
                    val longitude = location["longitude"] ?: 0.0

                    updateDriverMarker(latitude, longitude)

                    // Mettre à jour le texte d'information
                    tvDriverTracking.text = "🚖 Chauffeur: ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                } else {
                    tvDriverTracking.text = "⚠️ En attente de la position du chauffeur..."
                }
            }

            override fun onFailure(call: Call<Map<String, Double>>, t: Throwable) {
                tvDriverTracking.text = "⚠️ Connexion en cours..."
            }
        })
    }

    private fun updateDriverMarker(latitude: Double, longitude: Double) {
        if (latitude == 0.0 && longitude == 0.0) return

        val driverLocation = LatLng(latitude, longitude)

        if (driverMarker == null) {
            // Créer le marqueur du chauffeur
            driverMarker = mMap.addMarker(
                MarkerOptions()
                    .position(driverLocation)
                    .title("Votre chauffeur")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        } else {
            // Déplacer le marqueur existant
            driverMarker?.position = driverLocation
        }

        // Optionnel : Centrer la carte sur le chauffeur
        // mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLocation, 15f))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mMap.isMyLocationEnabled = true
                    getCurrentLocationAndShow()
                }
            } else {
                Toast.makeText(this, "Permission de localisation refusée", Toast.LENGTH_SHORT).show()
                showDefaultLocation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }
}