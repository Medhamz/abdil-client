package com.abdil.taxi

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.abdil.taxi.network.RetrofitClient
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class LiveSharingActivity : BaseActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "LiveSharing"
        private const val LOCATION_UPDATE_INTERVAL = 3000L // 3 secondes
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var tvShareCode: TextView
    private lateinit var tvShareLink: TextView
    private lateinit var btnCopyCode: Button
    private lateinit var btnCopyLink: Button
    private lateinit var btnShareWhatsApp: Button
    private lateinit var btnStopSharing: Button
    private lateinit var btnSimulate: Button  // ← NOUVEAU bouton simulateur
    private lateinit var tvViewerCount: TextView
    private lateinit var tvTripStatus: TextView
    private lateinit var progressBar: View

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null

    private var shareCode: String = ""
    private var rideId: Long = -1
    private var isSharing = false
    private var updateJob: Job? = null
    private var pollingJob: Job? = null
    private var viewerCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_sharing)

        tokenManager = TokenManager(this)
        rideId = intent.getLongExtra("RIDE_ID", -1)

        if (rideId == -1L) {
            Toast.makeText(this, "Erreur: course non trouvée", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupMap()
        checkLocationPermission()

        // LANCER LE PARTAGE APRÈS UN COURT DELAI
        Handler(Looper.getMainLooper()).postDelayed({
            startLiveSharing()
        }, 500)
    }

    private fun initViews() {
        tvShareCode = findViewById(R.id.tvShareCode)
        tvShareLink = findViewById(R.id.tvShareLink)
        btnCopyCode = findViewById(R.id.btnCopyCode)
        btnCopyLink = findViewById(R.id.btnCopyLink)
        btnShareWhatsApp = findViewById(R.id.btnShareWhatsApp)
        btnStopSharing = findViewById(R.id.btnStopSharing)
        btnSimulate = findViewById(R.id.btnSimulate)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvTripStatus = findViewById(R.id.tvTripStatus)
        progressBar = findViewById(R.id.progressBar)

        btnCopyCode.setOnClickListener { copyShareCode() }
        btnCopyLink.setOnClickListener { copyShareLink() }
        btnShareWhatsApp.setOnClickListener { shareViaWhatsApp() }
        btnStopSharing.setOnClickListener { stopSharing() }

        // Bouton simulateur pour tester sans GPS
        btnSimulate.setOnClickListener {
            val testLat = 33.5731
            val testLng = -7.5898
            updateLocationOnServer(testLat, testLng)
            updateMap(testLat, testLng)
            Toast.makeText(this, "📍 Position simulée envoyée (Casablanca)", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "🔧 Position simulée: lat=$testLat, lng=$testLng")
        }
        btnSimulate.visibility = View.VISIBLE
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Permission GPS accordée")
            checkGPSEnabled()
        } else {
            Log.d(TAG, "❌ Permission GPS non accordée, demande...")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    private fun checkGPSEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGPSEnabled) {
            Log.d(TAG, "⚠️ GPS désactivé")
            AlertDialog.Builder(this)
                .setTitle("GPS requis")
                .setMessage("Pour partager votre position en temps réel, veuillez activer le GPS.")
                .setPositiveButton("Activer") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Annuler") { _, _ ->
                    Toast.makeText(this, "GPS désactivé, utilisez le mode simulation", Toast.LENGTH_LONG).show()
                }
                .show()
        } else {
            Log.d(TAG, "✅ GPS activé")
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Permission accordée par l'utilisateur")
            checkGPSEnabled()
        } else {
            Log.d(TAG, "❌ Permission refusée")
            Toast.makeText(this, "Permission de localisation requise pour le partage", Toast.LENGTH_LONG).show()
        }
    }

    private fun startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
            .setIntervalMillis(LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(2000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d(TAG, "📍 Nouvelle position GPS: ${location.latitude}, ${location.longitude}")
                    updateLocationOnServer(location.latitude, location.longitude)
                    updateMap(location.latitude, location.longitude)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "✅ Mises à jour de localisation démarrées")
        }
    }

    private fun updateLocationOnServer(lat: Double, lng: Double) {
        if (!isSharing || shareCode.isEmpty()) {
            Log.d(TAG, "⚠️ Envoi bloqué: isSharing=$isSharing, shareCode=${if(shareCode.isEmpty()) "vide" else shareCode}")
            return
        }

        Log.d(TAG, "📤 Envoi position au serveur: code=$shareCode, lat=$lat, lng=$lng")

        RetrofitClient.apiService.updateLiveLocation(shareCode, lat, lng).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Position envoyée avec succès!")
                } else {
                    Log.e(TAG, "❌ Erreur HTTP: ${response.code()}")
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e(TAG, "❌ Échec connexion: ${t.message}")
            }
        })
    }

    private fun updateMap(lat: Double, lng: Double) {
        val position = LatLng(lat, lng)
        if (driverMarker == null) {
            driverMarker = googleMap?.addMarker(MarkerOptions().position(position).title("🚖 Ma position").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        } else {
            driverMarker?.position = position
        }
    }

    private fun startLiveSharing() {
        progressBar.visibility = View.VISIBLE

        RetrofitClient.apiService.createLiveShare(rideId).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    shareCode = data["shareCode"] ?: ""
                    isSharing = true

                    tvShareCode.text = shareCode
                    tvShareLink.text = "http://192.168.11.101:8080/track/index.html"

                    tvTripStatus.text = "🎯 Partage actif - Envoyez le code à vos proches"
                    tvTripStatus.setTextColor(Color.parseColor("#4CAF50"))

                    startViewerPolling()
                    Log.d(TAG, "✅ Live share créé avec code: $shareCode")
                    Toast.makeText(this@LiveSharingActivity, "✅ Partage activé! Code: $shareCode", Toast.LENGTH_LONG).show()
                } else {
                    showDemoMode()
                }
            }
            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Log.e(TAG, "❌ Erreur création live share: ${t.message}")
                showDemoMode()
            }
        })
    }

    private fun showDemoMode() {
        shareCode = generateDemoCode()
        isSharing = true

        tvShareCode.text = shareCode
        tvShareLink.text = "http://192.168.11.101:8080/track/index.html"
        tvTripStatus.text = "🎯 Mode démo - Partage actif"
        tvTripStatus.setTextColor(Color.parseColor("#4CAF50"))
        tvViewerCount.text = "👁️ Mode démonstration"

        Log.d(TAG, "🔧 Mode démo activé avec code: $shareCode")
        Toast.makeText(this, "⚠️ Mode démo: utilisez le bouton 'Simuler position'", Toast.LENGTH_LONG).show()
    }

    private fun generateDemoCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun startViewerPolling() {
        pollingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isSharing) {
                delay(5000)
                fetchViewerCount()
            }
        }
    }

    private fun fetchViewerCount() {
        if (shareCode.isEmpty()) return

        RetrofitClient.apiService.getLiveShareViewers(shareCode).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    viewerCount = (data["viewerCount"] as? Number)?.toInt() ?: 0
                    tvViewerCount.text = "👁️ $viewerCount personne(s) suit votre trajet"
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {}
        })
    }

    private fun copyShareCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("share_code", shareCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Code copié: $shareCode", Toast.LENGTH_SHORT).show()
    }

    private fun copyShareLink() {
        val link = "http://192.168.11.101:8080/track/index.html"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("share_link", link)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Lien copié!", Toast.LENGTH_SHORT).show()
    }

    private fun shareViaWhatsApp() {
        val link = "http://192.168.11.101:8080/track/index.html"
        val message = "🚖 *Suivez mon trajet en temps réel!*\n\nCode de suivi: *$shareCode*\n\n🔗 Lien: $link\n\n📝 Instructions:\n1. Ouvrez le lien ci-dessus\n2. Entrez le code: $shareCode\n3. Suivez mon trajet en direct!\n\n📍 Abdil Taxi - Sécurité et transparence"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            `package` = "com.whatsapp"
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp non installé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSharing() {
        AlertDialog.Builder(this)
            .setTitle("Arrêter le partage")
            .setMessage("Voulez-vous vraiment arrêter de partager votre position?")
            .setPositiveButton("Oui") { _, _ ->
                if (shareCode.isNotEmpty()) {
                    RetrofitClient.apiService.stopLiveShare(shareCode).enqueue(object : Callback<Void> {
                        override fun onResponse(call: Call<Void>, response: Response<Void>) {
                            stopSharingInternal()
                        }
                        override fun onFailure(call: Call<Void>, t: Throwable) {
                            stopSharingInternal()
                        }
                    })
                } else {
                    stopSharingInternal()
                }
            }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun stopSharingInternal() {
        isSharing = false
        updateJob?.cancel()
        pollingJob?.cancel()
        Log.d(TAG, "🛑 Partage arrêté")
        Toast.makeText(this, "Partage arrêté", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        pollingJob?.cancel()
        if (isSharing) {
            stopSharingInternal()
        }
    }
}