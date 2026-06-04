package com.abdil.taxi

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NfcPaymentActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var techLists: Array<Array<String>>
    private lateinit var tokenManager: TokenManager

    private lateinit var tvStatus: TextView
    private lateinit var tvAmount: TextView
    private lateinit var btnCancel: Button

    private var rideId: Long = -1
    private var amount: Double = 0.0
    private var nfcToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_payment)

        tokenManager = TokenManager(this)

        tvStatus = findViewById(R.id.tvNfcStatus)
        tvAmount = findViewById(R.id.tvNfcAmount)
        btnCancel = findViewById(R.id.btnCancelNfc)

        rideId = intent.getLongExtra("RIDE_ID", -1)
        amount = intent.getDoubleExtra("AMOUNT", 0.0)

        tvAmount.text = "💰 ${String.format("%.0f", amount)} FCFA"

        // Vérification de la disponibilité du NFC
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            if (nfcAdapter == null) {
                Toast.makeText(this, "NFC non disponible sur ce téléphone. Le paiement NFC est impossible.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur NFC: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(techFilter)
        techLists = arrayOf(arrayOf(Ndef::class.java.name))

        btnCancel.setOnClickListener { finish() }

        initNfcSession()
    }

    private fun initNfcSession() {
        tvStatus.text = "🔄 Initialisation..."

        RetrofitClient.apiService.initiateNfcPayment(
            clientId = tokenManager.getUserId(),
            rideId = rideId,
            amount = amount
        ).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    nfcToken = result["nfcToken"] ?: ""
                    tvStatus.text = "📱 Approchez votre téléphone de celui du chauffeur"
                    Toast.makeText(this@NfcPaymentActivity, "Prêt pour le paiement NFC", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "❌ Erreur d'initialisation"
                    Toast.makeText(this@NfcPaymentActivity, "Erreur", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                tvStatus.text = "❌ Erreur: ${t.message}"
                Toast.makeText(this@NfcPaymentActivity, "Erreur: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                sendPaymentData(tag)
            }
        }
    }

    private fun sendPaymentData(tag: Tag) {
        tvStatus.text = "📤 Envoi des données..."

        val message = "ABDIL_NFC|$nfcToken|$rideId|$amount"
        val ndefRecord = NdefRecord.createMime("application/com.abdil.taxi", message.toByteArray())
        val ndefMessage = NdefMessage(arrayOf(ndefRecord))

        val ndef = Ndef.get(tag)
        try {
            ndef.connect()
            if (ndef.isWritable) {
                ndef.writeNdefMessage(ndefMessage)
                tvStatus.text = "✅ Données envoyées !"
                Toast.makeText(this, "Paiement envoyé au chauffeur", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                tvStatus.text = "❌ Tag NFC non accessible"
            }
            ndef.close()
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur: ${e.message}"
            e.printStackTrace()
        }
    }
}