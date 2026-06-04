package com.abdil.taxi

import android.Manifest
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.messaging.FirebaseMessaging
import com.abdil.taxi.model.PaymentRequest
import com.abdil.taxi.model.PaymentResponse
import com.abdil.taxi.model.RideResponse
import com.abdil.taxi.model.WalletTransaction
import com.abdil.taxi.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat
import java.util.Locale
import java.util.UUID
import com.bumptech.glide.Glide

class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "AbdilTaxi"
    }

    private lateinit var tokenManager: TokenManager
    private val viewModel: MainViewModel by viewModels()
    private lateinit var locationHelper: LocationHelper
    private lateinit var distanceHelper: DistanceHelper
    private var calculationJob: Job? = null

    private var activeRideId: Long = -1
    private var activeDriverId: Long = -1
    private var chatAvailableToastShown = false
    private var pauseToastShown = false
    private var isSchedulePriceObserverActive = false

    // Déclaration des vues
    private lateinit var etClientName: TextInputEditText
    private lateinit var etClientPhone: TextInputEditText
    private lateinit var etPickup: TextInputEditText
    private lateinit var etDestination: TextInputEditText
    private lateinit var etDistance: TextInputEditText
    private lateinit var btnEstimate: Button
    private lateinit var btnBook: Button
    private lateinit var btnCurrentLocation: Button
    private lateinit var btnHistory: Button
    private lateinit var btnMap: Button
    private lateinit var btnChat: Button
    private lateinit var btnLogout: Button
    private lateinit var btnSos: Button
    private lateinit var cardResult: View
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cardRideStatus: View
    private lateinit var tvRideStatus: TextView
    private lateinit var tvDriverInfo: TextView
    private lateinit var btnChatWithDriver: Button
    private lateinit var spinnerLanguage: Spinner
    private lateinit var tvPriceDetail: TextView
    private lateinit var btnCancelRide: Button
    private lateinit var btnSelectRideType: Button
    private lateinit var btnSchedule: Button
    private lateinit var cbFemaleOnly: CheckBox
    private lateinit var cbPassByMosque: CheckBox
    private lateinit var btnGeneratePaymentLink: Button
    private lateinit var btnNfcPayment: Button

    // RadioButton pour les paiements (5 options)
    private lateinit var rbCash: RadioButton
    private lateinit var rbWallet: RadioButton
    private lateinit var rbQRCode: RadioButton
    private lateinit var rbPaymentLink: RadioButton
    private lateinit var rbNFC: RadioButton
    private lateinit var btnTaxiPub: Button

    // Wallet views
    private lateinit var tvWalletBalance: TextView
    private lateinit var btnRecharge: Button
    private lateinit var btnWalletHistory: Button

    private var currentRideType = "STANDARD"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenManager = TokenManager(this)

        if (!tokenManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        Log.d(TAG, "Client ID: ${tokenManager.getUserId()}")

        viewModel.userId = tokenManager.getUserId()
        viewModel.currentRideType = "STANDARD"
        locationHelper = LocationHelper(this)
        distanceHelper = DistanceHelper(this)

        initViews()
        setupLanguageSpinner()
        setupObservers()
        setupClickListeners()
        setupAutoDistanceListener()

        btnChat.isEnabled = false
        btnChatWithDriver.isEnabled = false

        etDistance.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.let {
                    var text = it.toString()
                    if (text.contains("،") || text.contains(",")) {
                        etDistance.removeTextChangedListener(this)
                        text = text.replace("،", ".")
                        text = text.replace(",", ".")
                        etDistance.setText(text)
                        etDistance.setSelection(text.length)
                        etDistance.addTextChangedListener(this)
                    }
                }
            }
        })

        btnCancelRide.setOnClickListener {
            if (activeRideId != -1L && (activeDriverId != -1L)) {
                showCancelDialog()
            } else {
                Toast.makeText(this, "Aucune course active à annuler", Toast.LENGTH_SHORT).show()
            }
        }

        btnSos.setOnClickListener {
            val intent = Intent(this, SosActivity::class.java)
            intent.putExtra("DRIVER_NAME", tvDriverInfo.text.toString())
            intent.putExtra("DRIVER_PHONE", "")
            startActivity(intent)
        }

        checkLocationPermission()
        loadActiveRide()
        startPollingActiveRide()
        sendFcmTokenToServer()

        loadWalletBalance()
    }

    private fun initViews() {
        etClientName = findViewById(R.id.etClientName)
        etClientPhone = findViewById(R.id.etClientPhone)
        etPickup = findViewById(R.id.etPickup)
        etDestination = findViewById(R.id.etDestination)
        etDistance = findViewById(R.id.etDistance)
        btnEstimate = findViewById(R.id.btnEstimate)
        btnBook = findViewById(R.id.btnBook)
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation)
        btnHistory = findViewById(R.id.btnHistory)
        btnMap = findViewById(R.id.btnMap)
        btnChat = findViewById(R.id.btnChat)
        btnLogout = findViewById(R.id.btnLogout)
        btnSos = findViewById(R.id.btnSos)
        cardResult = findViewById(R.id.cardResult)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvPrice = findViewById(R.id.tvPrice)
        tvStatus = findViewById(R.id.tvStatus)
        cardRideStatus = findViewById(R.id.cardRideStatus)
        tvRideStatus = findViewById(R.id.tvRideStatus)
        tvDriverInfo = findViewById(R.id.tvDriverInfo)
        btnChatWithDriver = findViewById(R.id.btnChatWithDriver)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        tvPriceDetail = findViewById(R.id.tvPriceDetail)
        btnCancelRide = findViewById(R.id.btnCancelRide)
        btnSelectRideType = findViewById(R.id.btnSelectRideType)
        btnSchedule = findViewById(R.id.btnSchedule)
        cbFemaleOnly = findViewById(R.id.cbFemaleOnly)
        cbPassByMosque = findViewById(R.id.cbPassByMosque)
        btnGeneratePaymentLink = findViewById(R.id.btnGeneratePaymentLink)
        btnNfcPayment = findViewById(R.id.btnNfcPayment)

        // RadioButton pour les paiements (5 options)
        rbCash = findViewById(R.id.rbCash)
        rbWallet = findViewById(R.id.rbWallet)
        rbQRCode = findViewById(R.id.rbQRCode)
        rbPaymentLink = findViewById(R.id.rbPaymentLink)
        rbNFC = findViewById(R.id.rbNFC)

        tvWalletBalance = findViewById(R.id.tvWalletBalance)
        btnRecharge = findViewById(R.id.btnRecharge)
        btnWalletHistory = findViewById(R.id.btnWalletHistory)

        btnRecharge.setOnClickListener { showRechargeDialog() }
        btnWalletHistory.setOnClickListener { showWalletHistory() }
        btnGeneratePaymentLink.setOnClickListener { generatePaymentLinkAfterAcceptance() }
        btnGeneratePaymentLink.visibility = View.GONE

        btnNfcPayment.setOnClickListener {
            initiateNfcPayment()
        }

        btnTaxiPub = findViewById(R.id.btnTaxiPub)
        btnTaxiPub.setOnClickListener {
            startActivity(Intent(this, AdvertisingActivity::class.java))
        }

        btnSelectRideType.text = "🚕 Standard - 150 FCFA/km"
        btnSelectRideType.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun showCancelDialog() {
        val reasons = listOf(
            "📝 Changement d'avis",
            "⏰ Temps d'attente trop long",
            "📍 Problème de localisation",
            "💰 Prix trop élevé",
            "✏️ Autre (précisez)"
        )

        val listView = ListView(this)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, reasons)

        val dialog = AlertDialog.Builder(this)
            .setTitle("❌ Annuler la course")
            .setMessage("Pourquoi souhaitez-vous annuler ?")
            .setView(listView)
            .setNegativeButton("✗ Retour", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            when (position) {
                0 -> showConfirmationDialog("Changement d'avis")
                1 -> showConfirmationDialog("Temps d'attente trop long")
                2 -> showConfirmationDialog("Problème de localisation")
                3 -> showConfirmationDialog("Prix trop élevé")
                4 -> showCustomReasonDialog()
            }
        }

        dialog.show()
    }

    private fun showConfirmationDialog(reason: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmation")
            .setMessage("Êtes-vous sûr de vouloir annuler cette course ?\n\nRaison : $reason")
            .setPositiveButton("✓ Oui, annuler") { _, _ ->
                cancelRide(reason)
            }
            .setNegativeButton("✗ Non, retour", null)
            .show()
    }

    private fun showCustomReasonDialog() {
        val input = EditText(this)
        input.hint = "Entrez votre raison..."
        input.setSingleLine(false)
        input.maxLines = 3
        input.setPadding(40, 20, 40, 20)

        AlertDialog.Builder(this)
            .setTitle("✏️ Autre raison")
            .setView(input)
            .setPositiveButton("✓ Confirmer") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isNotEmpty()) {
                    showConfirmationDialog(reason)
                } else {
                    Toast.makeText(this, "Veuillez entrer une raison", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("✗ Annuler", null)
            .show()
    }

    private fun cancelRide(reason: String) {
        if (activeRideId == -1L) {
            Toast.makeText(this, "Aucune course active", Toast.LENGTH_SHORT).show()
            return
        }

        val clientId = tokenManager.getUserId()
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Annulation en cours...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.cancelRideByClient(activeRideId, clientId, reason).enqueue(object : Callback<RideResponse> {
            override fun onResponse(call: Call<RideResponse>, response: Response<RideResponse>) {
                progressDialog.dismiss()
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "✅ Course annulée avec succès", Toast.LENGTH_LONG).show()
                    activeRideId = -1
                    activeDriverId = -1
                    cardRideStatus.visibility = View.GONE
                    btnChat.isEnabled = false
                    btnChatWithDriver.isEnabled = false
                    pauseToastShown = false
                    loadActiveRide()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Erreur lors de l'annulation", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<RideResponse>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf(getString(R.string.french), getString(R.string.english), getString(R.string.arabic))
        val codes = arrayOf("fr", "en", "ar")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        val savedLanguage = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("language", "fr")

        val position = codes.indexOf(savedLanguage)
        if (position >= 0) {
            spinnerLanguage.setSelection(position)
        }

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = codes[position]
                val currentLang = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "fr")
                if (selectedCode != currentLang) {
                    updateLanguage(selectedCode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun convertArabicToLatinNumbers(input: String): String {
        if (input.isBlank()) return input

        val arabicToLatin = mapOf(
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
        )

        var result = ""
        for (char in input) {
            result += arabicToLatin[char] ?: char
        }

        result = result.replace("،", ".")
        result = result.replace(",", ".")
        return result.trim()
    }

    private fun formatDistanceWithDot(distance: Double): String {
        val df = DecimalFormat("#.#")
        df.decimalFormatSymbols = java.text.DecimalFormatSymbols(Locale.US)
        return df.format(distance)
    }

    private fun convertLatinToArabicNumbers(input: String): String {
        val latinToArabic = mapOf(
            '0' to '٠', '1' to '١', '2' to '٢', '3' to '٣', '4' to '٤',
            '5' to '٥', '6' to '٦', '7' to '٧', '8' to '٨', '9' to '٩'
        )
        var result = ""
        for (char in input) {
            result += latinToArabic[char] ?: char
        }
        return result
    }

    private fun getCurrentLanguage(): String {
        return getSharedPreferences("settings", MODE_PRIVATE)
            .getString("language", "fr") ?: "fr"
    }

    private fun startPollingActiveRide() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                loadActiveRide()
                delay(5000)
            }
        }
    }

    private fun loadActiveRide() {
        val clientId = tokenManager.getUserId()
        if (clientId == -1L) return

        RetrofitClient.apiService.getActiveRideForClient(clientId).enqueue(object : Callback<RideResponse> {
            override fun onResponse(call: Call<RideResponse>, response: Response<RideResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val ride = response.body()!!
                    val previousRideId = activeRideId
                    activeRideId = ride.id
                    activeDriverId = ride.driverId ?: -1
                    val driverName = ride.driverName ?: "Chauffeur"
                    val status = ride.status

                    val isDriverOnPause = ride.driverIsOnPause ?: false
                    val driverPauseReason = ride.driverPauseReason ?: ""

                    cardRideStatus.visibility = View.VISIBLE

                    if (isDriverOnPause) {
                        tvRideStatus.text = "⏸️ CHAUFFEUR EN PAUSE"
                        tvRideStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
                        tvDriverInfo.text = "$driverName - $driverPauseReason"
                        tvDriverInfo.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
                        btnChat.isEnabled = false
                        btnChatWithDriver.isEnabled = false

                        if (!pauseToastShown) {
                            pauseToastShown = true
                            Toast.makeText(this@MainActivity, "⏸️ Le chauffeur est en pause: $driverPauseReason", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        tvRideStatus.text = if (status == "ACCEPTED") "✅ Course acceptée" else "🚖 Course en cours"
                        tvRideStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
                        tvDriverInfo.text = driverName
                        tvDriverInfo.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
                        btnChat.isEnabled = true
                        btnChatWithDriver.isEnabled = true

                        if (pauseToastShown) {
                            pauseToastShown = false
                        }
                    }

                    // ✅ Afficher le bouton de génération de lien si paiement par lien et course acceptée
                    if (ride.paymentMethod == "PAYMENT_LINK" && (status == "ACCEPTED" || status == "STARTED")) {
                        btnGeneratePaymentLink.visibility = View.VISIBLE
                    } else {
                        btnGeneratePaymentLink.visibility = View.GONE
                    }

                    if (ride.paymentMethod == "NFC" && (status == "ACCEPTED" || status == "STARTED")) {
                        btnNfcPayment.visibility = View.VISIBLE
                    } else {
                        btnNfcPayment.visibility = View.GONE
                    }

                    if (!chatAvailableToastShown && previousRideId == -1L && !isDriverOnPause) {
                        chatAvailableToastShown = true
                        Toast.makeText(this@MainActivity, getString(R.string.chat_available), Toast.LENGTH_LONG).show()
                    }

                    Log.d(TAG, "Course active: rideId=$activeRideId, driverId=$activeDriverId, isOnPause=$isDriverOnPause")
                } else if (response.code() == 404) {
                    activeRideId = -1
                    activeDriverId = -1
                    cardRideStatus.visibility = View.GONE
                    btnChat.isEnabled = false
                    btnChatWithDriver.isEnabled = false
                    btnGeneratePaymentLink.visibility = View.GONE
                    chatAvailableToastShown = false
                    pauseToastShown = false
                }
            }

            override fun onFailure(call: Call<RideResponse>, t: Throwable) {
                Log.e(TAG, "Erreur loadActiveRide: ${t.message}")
            }
        })
    }

    private fun setupClickListeners() {
        btnCurrentLocation.setOnClickListener { checkLocationPermission() }
        btnLogout.setOnClickListener {
            tokenManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        btnMap.setOnClickListener {
            if (activeDriverId != -1L) {
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("DRIVER_ID", activeDriverId)
                startActivity(intent)
            } else {
                startActivity(Intent(this, MapActivity::class.java))
            }
        }

        btnChatWithDriver.setOnClickListener { openChat() }
        btnChat.setOnClickListener { openChat() }

        btnEstimate.setOnClickListener {
            val pickup = etPickup.text.toString()
            val destination = etDestination.text.toString()
            var distanceStr = etDistance.text.toString()
            val passByMosque = cbPassByMosque.isChecked

            distanceStr = convertArabicToLatinNumbers(distanceStr.trim())
            val distance = distanceStr.toDoubleOrNull() ?: 0.0

            if (pickup.isBlank() || destination.isBlank()) {
                Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (distance <= 0) {
                Toast.makeText(this, getString(R.string.invalid_distance), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.calculatePriceWithMosque(pickup, destination, distance, currentRideType, passByMosque)
        }

        btnBook.setOnClickListener {
            val clientName = etClientName.text.toString()
            val clientPhone = etClientPhone.text.toString()
            val pickup = etPickup.text.toString()
            val destination = etDestination.text.toString()
            var distanceStr = etDistance.text.toString()
            val femaleOnly = cbFemaleOnly.isChecked
            val passByMosque = cbPassByMosque.isChecked

            val paymentMethod = when {
                rbCash.isChecked -> "CASH"
                rbWallet.isChecked -> "WALLET"
                rbQRCode.isChecked -> "QR_CODE"
                rbPaymentLink.isChecked -> "PAYMENT_LINK"
                rbNFC.isChecked -> "NFC"
                else -> {
                    Toast.makeText(this, "Veuillez sélectionner un mode de paiement", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            distanceStr = convertArabicToLatinNumbers(distanceStr.trim())
            val distance = distanceStr.toDoubleOrNull() ?: 0.0

            if (clientName.isBlank() || clientPhone.isBlank()) {
                Toast.makeText(this, getString(R.string.invalid_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pickup.isBlank() || destination.isBlank()) {
                Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (distance <= 0) {
                Toast.makeText(this, getString(R.string.invalid_distance), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            animateButton(btnBook)

            when (paymentMethod) {
                "WALLET" -> {
                    viewModel.estimatedPrice.observe(this) { priceResponse ->
                        if (priceResponse != null && priceResponse.estimatedPrice != null) {
                            val amount = priceResponse.estimatedPrice ?: 0.0
                            payWithWallet(amount, activeRideId) { success ->
                                if (success) {
                                    viewModel.bookRide(clientName, clientPhone, pickup, destination, distance,
                                        currentRideType, femaleOnly, passByMosque, "WALLET", "")
                                }
                            }
                            viewModel.estimatedPrice.removeObservers(this)
                        }
                    }
                }
                "QR_CODE" -> {
                    viewModel.estimatedPrice.observe(this) { priceResponse ->
                        if (priceResponse != null && priceResponse.estimatedPrice != null) {
                            viewModel.bookRide(clientName, clientPhone, pickup, destination, distance,
                                currentRideType, femaleOnly, passByMosque, "QR_CODE", "")
                            viewModel.rideResult.observe(this) { rideResponse ->
                                if (rideResponse != null) {
                                    activeRideId = rideResponse.id
                                    generateQRCodeForPayment()
                                    viewModel.rideResult.removeObservers(this)
                                }
                            }
                        }
                        viewModel.estimatedPrice.removeObservers(this)
                    }
                }
                "PAYMENT_LINK" -> {
                    viewModel.estimatedPrice.observe(this) { priceResponse ->
                        if (priceResponse != null && priceResponse.estimatedPrice != null) {
                            viewModel.bookRide(clientName, clientPhone, pickup, destination, distance,
                                currentRideType, femaleOnly, passByMosque, "PAYMENT_LINK", "")
                            viewModel.rideResult.observe(this) { rideResponse ->
                                if (rideResponse != null) {
                                    activeRideId = rideResponse.id
                                    Toast.makeText(this@MainActivity,
                                        "🔗 Course réservée. Une fois le chauffeur accepté, cliquez sur 'Générer lien' pour payer.",
                                        Toast.LENGTH_LONG).show()
                                    viewModel.rideResult.removeObservers(this)
                                }
                            }
                        }
                        viewModel.estimatedPrice.removeObservers(this)
                    }
                }
                "NFC" -> {
                    viewModel.estimatedPrice.observe(this) { priceResponse ->
                        if (priceResponse != null && priceResponse.estimatedPrice != null) {
                            viewModel.bookRide(clientName, clientPhone, pickup, destination, distance,
                                currentRideType, femaleOnly, passByMosque, "NFC", "")
                            viewModel.rideResult.observe(this) { rideResponse ->
                                if (rideResponse != null) {
                                    activeRideId = rideResponse.id
                                    // ✅ Afficher un message, pas lancer le NFC immédiatement
                                    Toast.makeText(this@MainActivity,
                                        "📱 Course réservée. Une fois le chauffeur accepté, cliquez sur 'Paiement NFC' pour payer.",
                                        Toast.LENGTH_LONG).show()
                                    viewModel.rideResult.removeObservers(this)
                                }
                            }
                        }
                        viewModel.estimatedPrice.removeObservers(this)
                    }
                }
                else -> { // CASH
                    viewModel.estimatedPrice.observe(this) { priceResponse ->
                        if (priceResponse != null && priceResponse.estimatedPrice != null) {
                            viewModel.bookRide(clientName, clientPhone, pickup, destination, distance,
                                currentRideType, femaleOnly, passByMosque, "CASH", "")
                            Toast.makeText(this@MainActivity,
                                "✅ Course réservée. Payez le chauffeur à la fin de la course.",
                                Toast.LENGTH_LONG).show()
                        }
                        viewModel.estimatedPrice.removeObservers(this)
                    }
                }
            }
        }

        btnSelectRideType.setOnClickListener {
            showRideTypeDialog()
        }

        btnSchedule.setOnClickListener {
            val pickup = etPickup.text.toString()
            val destination = etDestination.text.toString()
            var distanceStr = etDistance.text.toString()
            distanceStr = convertArabicToLatinNumbers(distanceStr.trim())
            val distance = distanceStr.toDoubleOrNull() ?: 0.0

            val clientName = etClientName.text.toString().trim()
            val clientPhone = etClientPhone.text.toString().trim()

            if (pickup.isBlank() || destination.isBlank()) {
                Toast.makeText(this, "Veuillez remplir départ et destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (clientName.isBlank() || clientPhone.isBlank()) {
                Toast.makeText(this, "Veuillez remplir votre nom et téléphone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (distance <= 0) {
                Toast.makeText(this, "Distance invalide. Veuillez cliquer sur 'Estimer' d'abord", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "📅 Préparation de la programmation...", Toast.LENGTH_SHORT).show()

            viewModel.calculatePrice(pickup, destination, distance, currentRideType)

            if (!isSchedulePriceObserverActive) {
                isSchedulePriceObserverActive = true
                viewModel.estimatedPrice.observe(this) { priceResponse ->
                    if (priceResponse != null && priceResponse.estimatedPrice != null) {
                        val intent = Intent(this, ScheduledRideActivity::class.java)
                        intent.putExtra("PICKUP_ADDRESS", pickup)
                        intent.putExtra("DESTINATION_ADDRESS", destination)
                        intent.putExtra("DISTANCE", distance)
                        intent.putExtra("ESTIMATED_PRICE", priceResponse.estimatedPrice ?: 0.0)
                        intent.putExtra("RIDE_TYPE", currentRideType)
                        intent.putExtra("CLIENT_NAME", clientName)
                        intent.putExtra("CLIENT_PHONE", clientPhone)
                        startActivity(intent)
                        viewModel.estimatedPrice.removeObservers(this)
                        isSchedulePriceObserverActive = false
                    }
                }
            }
        }
    }

    private fun openChat() {
        Log.d("ChatDebug", "activeRideId=$activeRideId, activeDriverId=$activeDriverId")
        if (activeRideId != -1L && activeDriverId != -1L) {
            val clientId = tokenManager.getUserId()
            Log.d("ChatDebug", "clientId=$clientId")
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("RIDE_ID", activeRideId)
            intent.putExtra("DRIVER_ID", activeDriverId)
            intent.putExtra("CLIENT_ID", clientId)
            startActivity(intent)
        } else {
            Toast.makeText(this, getString(R.string.no_active_ride), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAutoDistanceListener() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                autoCalculateDistance()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etPickup.addTextChangedListener(watcher)
        etDestination.addTextChangedListener(watcher)
    }

    private fun autoCalculateDistance() {
        val pickup = etPickup.text.toString()
        val destination = etDestination.text.toString()
        if (pickup.isNotBlank() && destination.isNotBlank()) {
            calculationJob?.cancel()
            calculationJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val distance = distanceHelper.calculateDistance(pickup, destination)
                    withContext(Dispatchers.Main) {
                        if (distance > 0) {
                            val distanceLatin = formatDistanceWithDot(distance)
                            val currentLang = getCurrentLanguage()
                            val displayText = if (currentLang == "ar") {
                                convertLatinToArabicNumbers(distanceLatin)
                            } else {
                                distanceLatin
                            }
                            etDistance.setText(displayText)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun getCurrentLocation() {
        CoroutineScope(Dispatchers.IO).launch {
            val location = locationHelper.getCurrentLocation()
            withContext(Dispatchers.Main) {
                if (location != null) {
                    etPickup.setText("${getString(R.string.current_location)} (${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)})")
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.estimatedPrice.observe(this) { priceResponse ->
            if (priceResponse != null) {
                cardResult.visibility = View.VISIBLE
                tvDistance.text = "${getString(R.string.distance)}: %.1f km".format(priceResponse.distance ?: 0.0)
                tvDuration.text = "${getString(R.string.duration)}: ${priceResponse.duration ?: "-- min"}"
                tvPrice.text = "${getString(R.string.price)}: %.0f FCFA".format(priceResponse.estimatedPrice ?: 0.0)
                tvPriceDetail.text = priceResponse.breakdown
                tvPriceDetail.visibility = View.VISIBLE
            }
        }

        viewModel.rideResult.observe(this) { rideResponse ->
            if (rideResponse != null) {
                tvStatus.text = getString(R.string.ride_confirmed)
                loadActiveRide()
            }
        }
    }

    private fun sendFcmTokenToServer() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_Client", "Token récupéré: $token")
                val userId = tokenManager.getUserId()
                val deviceId = UUID.randomUUID().toString()
                RetrofitClient.apiService.registerToken(userId, token, deviceId, "CLIENT")
                    .enqueue(object : Callback<Void> {
                        override fun onResponse(call: Call<Void>, response: Response<Void>) {
                            if (response.isSuccessful) Log.d("FCM_Client", "✅ Token envoyé au serveur")
                        }
                        override fun onFailure(call: Call<Void>, t: Throwable) {
                            Log.e("FCM_Client", "❌ Erreur: ${t.message}")
                        }
                    })
            }
        }
    }

    private fun showRideTypeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ride_type, null)
        val cardStandard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardStandard)
        val cardVip = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardVip)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        cardStandard.setOnClickListener {
            currentRideType = "STANDARD"
            viewModel.currentRideType = "STANDARD"
            btnSelectRideType.text = "🚕 Standard - 150 FCFA/km"
            btnSelectRideType.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            dialog.dismiss()
            Toast.makeText(this, "Mode Standard sélectionné", Toast.LENGTH_SHORT).show()
        }

        cardVip.setOnClickListener {
            currentRideType = "VIP"
            viewModel.currentRideType = "VIP"
            btnSelectRideType.text = "💎 VIP - 200 FCFA/km"
            btnSelectRideType.setBackgroundColor(ContextCompat.getColor(this, R.color.orange_700))
            dialog.dismiss()
            Toast.makeText(this, "Mode VIP sélectionné (200 FCFA/km)", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun animateButton(button: View) {
        val anim = AnimationUtils.loadAnimation(this, R.anim.button_click)
        button.startAnimation(anim)
    }

    private fun processPayment(amount: Double, method: String, phone: String, rideId: Long, callback: (Boolean, String) -> Unit) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Connexion au service de paiement...")
            setCancelable(false)
            show()
        }

        val customerName = etClientName.text.toString()
        val customerEmail = tokenManager.getUserEmail() ?: "client@abdiltaxi.com"
        val userId = tokenManager.getUserId()

        val request = PaymentRequest(
            rideId = rideId,
            userId = userId,
            amount = amount,
            paymentMethod = method,
            phoneNumber = null,
            cardToken = null,
            customerName = customerName,
            customerEmail = customerEmail
        )

        RetrofitClient.apiService.initiatePayment(request).enqueue(object : Callback<PaymentResponse> {
            override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    when (result.status) {
                        "SUCCESS" -> {
                            showReceiptDialog(amount, method, result.transactionId, phone)
                            callback(true, result.transactionId)
                        }
                        "PENDING" -> {
                            Toast.makeText(this@MainActivity, "⏳ Paiement en cours de validation...", Toast.LENGTH_LONG).show()
                            callback(true, result.transactionId)
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "❌ ${result.message}", Toast.LENGTH_LONG).show()
                            callback(false, "")
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Erreur de connexion au serveur", Toast.LENGTH_LONG).show()
                    callback(false, "")
                }
            }
            override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
                callback(false, "")
            }
        })
    }

    private fun showReceiptDialog(amount: Double, paymentMethod: String, transactionId: String, phoneNumber: String) {
        val methodName = when (paymentMethod) {
            "CASH" -> "Espèces"
            "WALLET" -> "Porte-monnaie"
            "QR_CODE" -> "QR Code"
            else -> paymentMethod
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_receipt, null)

        val tvAmount = dialogView.findViewById<TextView>(R.id.tvReceiptAmount)
        val tvMethod = dialogView.findViewById<TextView>(R.id.tvReceiptMethod)
        val tvTransactionId = dialogView.findViewById<TextView>(R.id.tvReceiptTransactionId)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvReceiptPhone)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvReceiptDate)
        val btnClose = dialogView.findViewById<Button>(R.id.btnReceiptClose)

        tvAmount.text = "${String.format("%.0f", amount)} FCFA"
        tvMethod.text = methodName
        tvTransactionId.text = transactionId
        tvPhone.visibility = View.GONE
        tvDate.text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialogView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        btnClose.setOnClickListener { dialog.dismiss() }
    }

    // ==================== WALLET METHODS ====================

    private fun loadWalletBalance() {
        val userId = tokenManager.getUserId()
        if (userId == -1L) return

        RetrofitClient.apiService.getWalletBalance(userId).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful && response.body() != null) {
                    val balance = response.body()!!["balance"] as? Double ?: 0.0
                    tvWalletBalance.text = String.format("%.0f", balance) + " FCFA"
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                Log.e(TAG, "Erreur chargement solde: ${t.message}")
            }
        })
    }

    private fun showRechargeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recharge, null)

        val etAmount = dialogView.findViewById<EditText>(R.id.etRechargeAmount)
        val rbCoupon = dialogView.findViewById<RadioButton>(R.id.rbCoupon)
        val etCouponCode = dialogView.findViewById<EditText>(R.id.etCouponCode)
        val tilCouponCode = dialogView.findViewById<TextInputLayout>(R.id.tilCouponCode)

        val rbOrange = dialogView.findViewById<RadioButton>(R.id.rbOrangeMoneyRecharge)
        val rbAirtel = dialogView.findViewById<RadioButton>(R.id.rbAirtelMoneyRecharge)
        val etPhone = dialogView.findViewById<EditText>(R.id.etRechargePhone)
        val tilPhone = dialogView.findViewById<TextInputLayout>(R.id.tilRechargePhone)

        rbOrange.visibility = View.GONE
        rbAirtel.visibility = View.GONE
        tilPhone.visibility = View.GONE

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupRecharge)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            tilCouponCode.visibility = if (checkedId == R.id.rbCoupon) View.VISIBLE else View.GONE
            etAmount.visibility = if (checkedId == R.id.rbCoupon) View.GONE else View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("💰 Recharger mon porte-monnaie")
            .setView(dialogView)
            .setPositiveButton("Recharger") { _, _ ->
                when {
                    rbCoupon.isChecked -> {
                        val code = etCouponCode.text.toString().trim().uppercase()
                        if (code.isEmpty()) {
                            Toast.makeText(this, "Entrez un code coupon", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        redeemCoupon(code)
                    }
                    else -> {
                        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                        if (amount <= 0) {
                            Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        processRecharge(amount, "CASH", "")
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        dialog.show()
    }

    private fun redeemCoupon(code: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Validation du coupon...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.redeemCoupon(code, tokenManager.getUserId())
            .enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                    progressDialog.dismiss()
                    if (response.isSuccessful && response.body() != null) {
                        val result = response.body()!!
                        val success = result["success"] as? Boolean ?: false
                        if (success) {
                            val message = result["message"] as? String ?: "Recharge réussie"
                            Toast.makeText(this@MainActivity, "✅ $message", Toast.LENGTH_LONG).show()
                            loadWalletBalance()
                        } else {
                            val message = result["message"] as? String ?: "Code invalide"
                            Toast.makeText(this@MainActivity, "❌ $message", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Erreur de validation", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun processRecharge(amount: Double, method: String, phone: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Traitement du rechargement...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.rechargeWallet(
            userId = tokenManager.getUserId(),
            amount = amount,
            paymentMethod = method,
            phoneNumber = null
        ).enqueue(object : Callback<WalletTransaction> {
            override fun onResponse(call: Call<WalletTransaction>, response: Response<WalletTransaction>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@MainActivity, "✅ Rechargement réussi !", Toast.LENGTH_LONG).show()
                    loadWalletBalance()
                    showReceiptDialog(amount, method, response.body()!!.reference, phone)
                } else {
                    Toast.makeText(this@MainActivity, "❌ Échec du rechargement", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<WalletTransaction>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showWalletHistory() {
        val userId = tokenManager.getUserId()
        if (userId == -1L) return

        RetrofitClient.apiService.getWalletTransactions(userId).enqueue(object : Callback<List<WalletTransaction>> {
            override fun onResponse(call: Call<List<WalletTransaction>>, response: Response<List<WalletTransaction>>) {
                if (response.isSuccessful && response.body() != null) {
                    val transactions = response.body()!!
                    if (transactions.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Aucune transaction", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val message = StringBuilder("📜 Historique des transactions:\n\n")
                    transactions.forEach { t ->
                        val sign = if (t.type == "CREDIT") "+" else "-"
                        val date = t.createdAt.split("T")[0]
                        message.append("$date : $sign${String.format("%.0f", t.amount)} FCFA (${t.type})\n")
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Historique du porte-monnaie")
                        .setMessage(message.toString())
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            override fun onFailure(call: Call<List<WalletTransaction>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun payWithWallet(amount: Double, rideId: Long, callback: (Boolean) -> Unit) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Paiement en cours...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.payWithWallet(
            userId = tokenManager.getUserId(),
            amount = amount,
            rideId = rideId.toString()
        ).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val success = response.body()!!["success"] as? Boolean ?: false
                    if (success) {
                        Toast.makeText(this@MainActivity, "✅ Paiement effectué depuis le porte-monnaie", Toast.LENGTH_LONG).show()
                        loadWalletBalance()
                        callback(true)
                    } else {
                        val message = response.body()!!["message"] as? String ?: "Solde insuffisant"
                        Toast.makeText(this@MainActivity, "❌ $message", Toast.LENGTH_LONG).show()
                        callback(false)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "❌ Erreur de paiement", Toast.LENGTH_LONG).show()
                    callback(false)
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
                callback(false)
            }
        })
    }

    // ==================== QR CODE PAYMENT ====================

    private fun generateQRCodeForPayment() {
        val amount = viewModel.estimatedPrice.value?.estimatedPrice ?: 0.0
        if (amount <= 0) {
            Toast.makeText(this, "Veuillez d'abord estimer le prix", Toast.LENGTH_SHORT).show()
            return
        }

        if (activeRideId == -1L) {
            Toast.makeText(this, "Veuillez d'abord réserver une course", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Génération du QR Code...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.generateQRCode(
            userId = tokenManager.getUserId(),
            amount = amount,
            rideId = activeRideId
        ).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val qrCodeUrl = response.body()!!["qrCodeUrl"]
                    val amountStr = response.body()!!["amount"]
                    showQRCodeDialog(qrCodeUrl, amountStr)
                } else {
                    Toast.makeText(this@MainActivity, "Erreur génération QR Code", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showQRCodeDialog(qrCodeUrl: String?, amountStr: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_qrcode, null)
        val ivQRCode = dialogView.findViewById<ImageView>(R.id.ivQRCode)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvQRCodeAmount)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShareQRCode)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseQRCode)

        tvAmount.text = "💰 ${amountStr ?: "0"} FCFA"

        if (qrCodeUrl != null) {
            Glide.with(this).load(qrCodeUrl).into(ivQRCode)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Code QR Abdil Taxi - Montant: $amountStr FCFA\n$qrCodeUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Partager"))
        }

        btnClose.setOnClickListener { dialog.dismiss() }
    }

    // ==================== PAIEMENT PAR LIEN ====================

    private fun generatePaymentLinkAfterAcceptance() {
        val amount = viewModel.estimatedPrice.value?.estimatedPrice ?: 0.0
        if (amount <= 0) {
            // Récupérer le prix depuis la course active
            RetrofitClient.apiService.getActiveRideForClient(tokenManager.getUserId()).enqueue(object : Callback<RideResponse> {
                override fun onResponse(call: Call<RideResponse>, response: Response<RideResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val ride = response.body()!!
                        proceedGeneratePaymentLink(ride.estimatedPrice ?: 0.0)
                    } else {
                        Toast.makeText(this@MainActivity, "Impossible de récupérer le prix", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<RideResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
            return
        }
        proceedGeneratePaymentLink(amount)
    }

    private fun proceedGeneratePaymentLink(amount: Double) {
        if (activeRideId == -1L) {
            Toast.makeText(this, "Aucune course active", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Génération du lien...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.generatePaymentLink(activeRideId, tokenManager.getUserId(), amount)
            .enqueue(object : Callback<Map<String, String>> {
                override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                    progressDialog.dismiss()
                    if (response.isSuccessful && response.body() != null) {
                        val paymentLink = response.body()!!["paymentLink"]
                        showPaymentLinkDialog(paymentLink)
                    } else {
                        Toast.makeText(this@MainActivity, "Erreur génération lien", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showPaymentLinkDialog(link: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_link, null)
        val tvLink = dialogView.findViewById<TextView>(R.id.tvPaymentLink)
        val btnCopy = dialogView.findViewById<Button>(R.id.btnCopyLink)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShareLink)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseLink)
        val tvInstruction = dialogView.findViewById<TextView>(R.id.tvInstruction)

        tvLink.text = link
        tvInstruction.text = "📌 Donnez ce lien au chauffeur. Il l'utilisera dans son application pour valider le paiement."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("payment_link", link)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Lien copié ! Donnez-le au chauffeur", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "🔗 Lien de paiement Abdil Taxi : $link\n\nDonnez ce lien au chauffeur pour qu'il valide le paiement.")
            }
            startActivity(Intent.createChooser(shareIntent, "Partager"))
        }

        btnClose.setOnClickListener { dialog.dismiss() }
    }

    // ==================== PAIEMENT NFC ====================

    private fun initiateNfcPayment() {
        val amount = viewModel.estimatedPrice.value?.estimatedPrice ?: 0.0
        if (amount <= 0) {
            Toast.makeText(this, "Veuillez d'abord estimer le prix", Toast.LENGTH_SHORT).show()
            return
        }

        if (activeRideId == -1L) {
            Toast.makeText(this, "Veuillez d'abord réserver une course", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, NfcPaymentActivity::class.java)
        intent.putExtra("RIDE_ID", activeRideId)
        intent.putExtra("AMOUNT", amount)
        startActivity(intent)
    }
}