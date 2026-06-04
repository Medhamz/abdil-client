package com.abdil.taxi

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.abdil.taxi.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SosActivity : BaseActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var tvAlertMessage: TextView
    private lateinit var btnCallPolice: Button
    private lateinit var btnCallContact: Button
    private lateinit var btnSendLocation: Button
    private lateinit var btnCancel: Button

    private var driverName: String = ""
    private var driverPhone: String = ""
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        tokenManager = TokenManager(this)
        driverName = intent.getStringExtra("DRIVER_NAME") ?: "Chauffeur"
        driverPhone = intent.getStringExtra("DRIVER_PHONE") ?: ""

        initViews()
        getCurrentLocation()
    }

    private fun initViews() {
        tvAlertMessage = findViewById(R.id.tvAlertMessage)
        btnCallPolice = findViewById(R.id.btnCallPolice)
        btnCallContact = findViewById(R.id.btnCallContact)
        btnSendLocation = findViewById(R.id.btnSendLocation)
        btnCancel = findViewById(R.id.btnCancel)

        tvAlertMessage.text = "⚠️ Êtes-vous en danger ?\n\nContactez immédiatement les secours ou partagez votre position."

        btnCallPolice.setOnClickListener {
            callPolice()
        }

        btnCallContact.setOnClickListener {
            callEmergencyContact()
        }

        btnSendLocation.setOnClickListener {
            sendLocationToEmergency()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun getCurrentLocation() {
        val locationHelper = LocationHelper(this)
        // Utiliser la méthode suspend correctement avec coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val location = locationHelper.getCurrentLocation()
            withContext(Dispatchers.Main) {
                if (location != null) {
                    currentLat = location.latitude
                    currentLng = location.longitude
                } else {
                    Toast.makeText(this@SosActivity, "Position non disponible", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun callPolice() {
        val phoneNumber = "8383" // Police au Niger
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CALL_PHONE), 101)
        }
    }

    private fun callEmergencyContact() {
        val emergencyNumber = "+22794008958"
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber"))
        startActivity(intent)
    }

    private fun sendLocationToEmergency() {
        if (currentLat != 0.0 && currentLng != 0.0) {
            val mapsUrl = "https://maps.google.com/?q=$currentLat,$currentLng"
            val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:+22794008958"))
            smsIntent.putExtra("sms_body", "URGENCE - Je suis en danger ! Voici ma position : $mapsUrl")
            startActivity(smsIntent)
        } else {
            Toast.makeText(this, "Position non disponible, réessayez", Toast.LENGTH_SHORT).show()
            getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            callPolice()
        }
    }
}