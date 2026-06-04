package com.abdil.taxi

import android.app.ProgressDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.abdil.taxi.model.AdCampaign
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AdvertisingActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var etProductName: EditText
    private lateinit var etDescription: EditText
    private lateinit var rb1Month: RadioButton
    private lateinit var rb6Months: RadioButton
    private lateinit var rb1Year: RadioButton
    private lateinit var rbPayWallet: RadioButton
    private lateinit var rbPayCash: RadioButton
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advertising)

        tokenManager = TokenManager(this)

        etProductName = findViewById(R.id.etProductName)
        etDescription = findViewById(R.id.etDescription)
        rb1Month = findViewById(R.id.rb1Month)
        rb6Months = findViewById(R.id.rb6Months)
        rb1Year = findViewById(R.id.rb1Year)
        rbPayWallet = findViewById(R.id.rbPayWallet)
        rbPayCash = findViewById(R.id.rbPayCash)
        btnSubmit = findViewById(R.id.btnSubmitAd)

        btnSubmit.setOnClickListener { submitAdvertising() }
    }

    private fun submitAdvertising() {
        val product = etProductName.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val duration = when {
            rb1Month.isChecked -> "1 MONTH"
            rb6Months.isChecked -> "6 MONTHS"
            rb1Year.isChecked -> "1 YEAR"
            else -> {
                Toast.makeText(this, "Choisissez une durée", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val paymentMethod = when {
            rbPayWallet.isChecked -> "WALLET"
            rbPayCash.isChecked -> "CASH"
            else -> {
                Toast.makeText(this, "Choisissez un mode de paiement", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (product.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
            return
        }

        val ad = AdCampaign(
            clientId = tokenManager.getUserId(),
            clientName = tokenManager.getUserName() ?: "",
            clientPhone = tokenManager.getUserPhone() ?: "",
            productName = product,
            description = description,
            duration = duration,
            paymentMethod = paymentMethod
        )

        val progress = ProgressDialog(this).apply {
            setMessage("Envoi en cours...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.createAd(ad).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                progress.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    val success = result["success"] as? Boolean ?: false
                    if (success) {
                        val campaignId = (result["campaignId"] as? Number)?.toLong() ?: -1
                        val price = result["price"] as? Double ?: 0.0
                        // Payer maintenant
                        payForAd(campaignId, price, paymentMethod)
                    } else {
                        Toast.makeText(this@AdvertisingActivity, result["message"] as? String ?: "Erreur", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@AdvertisingActivity, "Erreur serveur", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                progress.dismiss()
                Toast.makeText(this@AdvertisingActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun payForAd(campaignId: Long, price: Double, paymentMethod: String) {
        val progress = ProgressDialog(this).apply {
            setMessage("Traitement du paiement...")
            setCancelable(false)
            show()
        }

        RetrofitClient.apiService.payForAd(campaignId, tokenManager.getUserId(), paymentMethod)
            .enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                    progress.dismiss()
                    if (response.isSuccessful && response.body() != null) {
                        val result = response.body()!!
                        val success = result["success"] as? Boolean ?: false
                        val message = result["message"] as? String ?: ""
                        if (success) {
                            AlertDialog.Builder(this@AdvertisingActivity)
                                .setTitle("✅ Demande envoyée")
                                .setMessage(message)
                                .setPositiveButton("OK") { _, _ -> finish() }
                                .show()
                        } else {
                            Toast.makeText(this@AdvertisingActivity, "❌ $message", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@AdvertisingActivity, "Erreur paiement", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    progress.dismiss()
                    Toast.makeText(this@AdvertisingActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }
}