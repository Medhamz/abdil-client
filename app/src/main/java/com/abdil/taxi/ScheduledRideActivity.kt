package com.abdil.taxi

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.abdil.taxi.model.ScheduledRide
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ScheduledRideActivity : BaseActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var tvSelectedDateTime: TextView
    private lateinit var tvPickupAddress: TextView
    private lateinit var tvDestinationAddress: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPrice: TextView
    private lateinit var btnDatePicker: Button
    private lateinit var btnTimePicker: Button
    private lateinit var btnSchedule: Button
    private lateinit var tvMessage: TextView

    private var selectedDate: Long = 0
    private var selectedHour = 0
    private var selectedMinute = 0
    private var pickupAddress = ""
    private var destinationAddress = ""
    private var distance = 0.0
    private var estimatedPrice = 0.0
    private var rideType = "STANDARD"

    // ✅ VARIABLES POUR LE NOM ET TÉLÉPHONE
    private var clientName = ""
    private var clientPhone = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_ride)

        tokenManager = TokenManager(this)

        // Récupérer les données de la course
        pickupAddress = intent.getStringExtra("PICKUP_ADDRESS") ?: ""
        destinationAddress = intent.getStringExtra("DESTINATION_ADDRESS") ?: ""
        distance = intent.getDoubleExtra("DISTANCE", 0.0)
        estimatedPrice = intent.getDoubleExtra("ESTIMATED_PRICE", 0.0)
        rideType = intent.getStringExtra("RIDE_TYPE") ?: "STANDARD"

        // ✅ RÉCUPÉRER LE NOM ET TÉLÉPHONE DEPUIS L'INTENT
        clientName = intent.getStringExtra("CLIENT_NAME") ?: ""
        clientPhone = intent.getStringExtra("CLIENT_PHONE") ?: ""

        initViews()
        displayRideSummary()
        setupDateTimePickers()
        setupScheduleButton()
    }

    private fun initViews() {
        tvSelectedDateTime = findViewById(R.id.tvSelectedDateTime)
        tvPickupAddress = findViewById(R.id.tvPickupAddress)
        tvDestinationAddress = findViewById(R.id.tvDestinationAddress)
        tvDistance = findViewById(R.id.tvDistance)
        tvPrice = findViewById(R.id.tvPrice)
        btnDatePicker = findViewById(R.id.btnDatePicker)
        btnTimePicker = findViewById(R.id.btnTimePicker)
        btnSchedule = findViewById(R.id.btnSchedule)
        tvMessage = findViewById(R.id.tvMessage)
    }

    private fun displayRideSummary() {
        tvPickupAddress.text = "📍 Départ: $pickupAddress"
        tvDestinationAddress.text = "🏁 Destination: $destinationAddress"
        tvDistance.text = "📏 Distance: ${String.format("%.1f", distance)} km"
        tvPrice.text = "💰 Prix: ${String.format("%.0f", estimatedPrice)} FCFA"
    }

    private fun setupDateTimePickers() {
        val calendar = Calendar.getInstance()

        btnDatePicker.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.timeInMillis
                    updateDateTimeDisplay()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnTimePicker.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    updateDateTimeDisplay()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun updateDateTimeDisplay() {
        if (selectedDate != 0L && (selectedHour != 0 || selectedMinute != 0)) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(Date(selectedDate))
            tvSelectedDateTime.text = "📅 $dateStr à ${String.format("%02d:%02d", selectedHour, selectedMinute)}"
            tvSelectedDateTime.setTextColor(resources.getColor(android.R.color.black))
        } else if (selectedDate != 0L) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            tvSelectedDateTime.text = "📅 ${dateFormat.format(Date(selectedDate))} - Sélectionnez l'heure"
        } else if (selectedHour != 0 || selectedMinute != 0) {
            tvSelectedDateTime.text = "⏰ ${String.format("%02d:%02d", selectedHour, selectedMinute)} - Sélectionnez la date"
        }
    }

    private fun setupScheduleButton() {
        btnSchedule.setOnClickListener {
            if (selectedDate == 0L || (selectedHour == 0 && selectedMinute == 0)) {
                Toast.makeText(this, "Veuillez sélectionner la date et l'heure", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ VÉRIFIER QUE LE NOM ET TÉLÉPHONE SONT PRÉSENTS
            if (clientName.isEmpty() || clientPhone.isEmpty()) {
                Toast.makeText(this, "Erreur: nom ou téléphone manquant", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedDate
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            calendar.set(Calendar.SECOND, 0)

            val scheduledDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(calendar.time)

            val userId = tokenManager.getUserId()

            val scheduledRide = ScheduledRide(
                userId = userId,
                clientName = clientName,      // ✅ UTILISER LA VARIABLE RÉCUPÉRÉE
                clientPhone = clientPhone,    // ✅ UTILISER LA VARIABLE RÉCUPÉRÉE
                pickupAddress = pickupAddress,
                destinationAddress = destinationAddress,
                distance = distance,
                estimatedPrice = estimatedPrice,
                rideType = rideType,
                scheduledDateTime = scheduledDateTime
            )

            btnSchedule.isEnabled = false
            btnSchedule.text = "⏳ Programmation en cours..."
            tvMessage.visibility = android.view.View.VISIBLE
            tvMessage.text = "⏳ Programmation en cours..."

            RetrofitClient.apiService.createScheduledRide(scheduledRide).enqueue(object : Callback<ScheduledRide> {
                override fun onResponse(call: Call<ScheduledRide>, response: Response<ScheduledRide>) {
                    btnSchedule.isEnabled = true
                    btnSchedule.text = "✅ Programmer la course"
                    tvMessage.visibility = android.view.View.GONE

                    if (response.isSuccessful) {
                        AlertDialog.Builder(this@ScheduledRideActivity)
                            .setTitle("✅ Course programmée")
                            .setMessage("Votre course a été programmée avec succès !\n\nDate: ${tvSelectedDateTime.text}\n\nUn rappel vous sera envoyé 30 minutes avant.")
                            .setPositiveButton("OK") { _, _ ->
                                finish()
                            }
                            .show()
                    } else {
                        Toast.makeText(this@ScheduledRideActivity, "❌ Erreur lors de la programmation", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ScheduledRide>, t: Throwable) {
                    btnSchedule.isEnabled = true
                    btnSchedule.text = "✅ Programmer la course"
                    tvMessage.visibility = android.view.View.GONE
                    Toast.makeText(this@ScheduledRideActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}