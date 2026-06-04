package com.abdil.taxi

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdil.taxi.model.RideResponse
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.abdil.taxi.model.ReviewRequest
import com.abdil.taxi.model.ReviewResponse

class HistoryActivity : BaseActivity() {

    companion object {
        private const val TAG = "ReviewDebug"
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeleteSelected: Button
    private lateinit var tvEmptyHistory: TextView
    private lateinit var tvClientNameHistory: TextView

    private lateinit var historyAdapter: HistoryAdapter
    private var rides = mutableListOf<RideResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        tokenManager = TokenManager(this)

        if (!tokenManager.isLoggedIn()) {
            finish()
            return
        }

        tvClientNameHistory = findViewById(R.id.tvClientNameHistory)
        rvHistory = findViewById(R.id.rvHistory)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)

        tvClientNameHistory.text = "Client: ${tokenManager.getUserName() ?: "---"}"

        historyAdapter = HistoryAdapter(rides,
            onSelectionChanged = { _, _ -> },
            onRateClick = { ride -> checkAndShowRatingDialog(ride) }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        btnSelectAll.setOnClickListener {
            historyAdapter.selectAll()
        }

        btnDeleteSelected.setOnClickListener {
            val selectedIds = historyAdapter.getSelectedRideIds()
            if (selectedIds.isNotEmpty()) {
                deleteSelectedRides(selectedIds)
            } else {
                Toast.makeText(this, "Aucune course sélectionnée", Toast.LENGTH_SHORT).show()
            }
        }

        loadHistory()
    }

    private fun loadHistory() {
        val clientId = tokenManager.getUserId()
        if (clientId == -1L) {
            tvEmptyHistory.visibility = android.view.View.VISIBLE
            rvHistory.visibility = android.view.View.GONE
            return
        }

        Log.d(TAG, "=== CHARGEMENT HISTORIQUE ===")
        Log.d(TAG, "clientId: $clientId")

        RetrofitClient.apiService.getClientRides(clientId).enqueue(object : Callback<List<RideResponse>> {
            override fun onResponse(call: Call<List<RideResponse>>, response: Response<List<RideResponse>>) {
                if (response.isSuccessful && response.body() != null) {
                    rides.clear()
                    rides.addAll(response.body()!!)
                    historyAdapter.updateList(rides)
                    Log.d(TAG, "✅ ${rides.size} courses chargées")

                    if (rides.isEmpty()) {
                        tvEmptyHistory.visibility = android.view.View.VISIBLE
                        rvHistory.visibility = android.view.View.GONE
                    } else {
                        tvEmptyHistory.visibility = android.view.View.GONE
                        rvHistory.visibility = android.view.View.VISIBLE
                    }
                } else {
                    Log.e(TAG, "❌ Erreur chargement: ${response.code()}")
                    tvEmptyHistory.visibility = android.view.View.VISIBLE
                    rvHistory.visibility = android.view.View.GONE
                }
            }

            override fun onFailure(call: Call<List<RideResponse>>, t: Throwable) {
                Log.e(TAG, "❌ Échec connexion: ${t.message}")
                Toast.makeText(this@HistoryActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
                tvEmptyHistory.visibility = android.view.View.VISIBLE
                rvHistory.visibility = android.view.View.GONE
            }
        })
    }

    private fun checkAndShowRatingDialog(ride: RideResponse) {
        Log.d(TAG, "=== VÉRIFICATION AVIS ===")
        Log.d(TAG, "rideId: ${ride.id}")
        Log.d(TAG, "status: ${ride.status}")

        if (ride.status != "COMPLETED") {
            Log.d(TAG, "❌ Course non terminée, status: ${ride.status}")
            Toast.makeText(this, "Vous ne pouvez noter qu'une course terminée", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Vérification en cours...", Toast.LENGTH_SHORT).show()

        RetrofitClient.apiService.checkReviewExists(ride.id).enqueue(object : Callback<Map<String, Boolean>> {
            override fun onResponse(call: Call<Map<String, Boolean>>, response: Response<Map<String, Boolean>>) {
                val alreadyRated = response.body()?.get("exists") == true
                Log.d(TAG, "alreadyRated: $alreadyRated")
                Log.d(TAG, "response code: ${response.code()}")

                if (alreadyRated) {
                    Toast.makeText(this@HistoryActivity, "Vous avez déjà noté cette course", Toast.LENGTH_LONG).show()
                } else {
                    showRatingDialog(ride)
                }
            }

            override fun onFailure(call: Call<Map<String, Boolean>>, t: Throwable) {
                Log.e(TAG, "❌ Erreur vérification: ${t.message}")
                showRatingDialog(ride)
            }
        })
    }

    private fun showRatingDialog(ride: RideResponse) {
        Log.d(TAG, "=== AFFICHAGE DIALOGUE NOTATION ===")
        Log.d(TAG, "rideId: ${ride.id}, driverName: ${ride.driverName}")

        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_rating, null)
            val ratingBar = dialogView.findViewById<android.widget.RatingBar>(R.id.ratingBar)
            val etComment = dialogView.findViewById<android.widget.EditText>(R.id.etComment)
            val tvRatingHint = dialogView.findViewById<android.widget.TextView>(R.id.tvRatingHint)

            ratingBar.onRatingBarChangeListener = android.widget.RatingBar.OnRatingBarChangeListener { _, rating, _ ->
                when (rating.toInt()) {
                    1 -> tvRatingHint.text = "⭐ Très insatisfait"
                    2 -> tvRatingHint.text = "⭐ Insatisfait"
                    3 -> tvRatingHint.text = "⭐⭐⭐ Satisfait"
                    4 -> tvRatingHint.text = "⭐⭐⭐⭐ Très satisfait"
                    5 -> tvRatingHint.text = "⭐⭐⭐⭐⭐ Excellent !"
                    else -> tvRatingHint.text = "Tapez sur les étoiles pour noter"
                }
            }

            val dateStr = try {
                ride.createdAt.split("T")[0]
            } catch (e: Exception) {
                ride.createdAt
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("Noter votre course")
                .setMessage("Course du $dateStr avec ${ride.driverName ?: "chauffeur"}")
                .setView(dialogView)
                .setPositiveButton("Envoyer") { _, _ ->
                    val rating = ratingBar.rating.toInt()
                    if (rating == 0) {
                        Toast.makeText(this, "Veuillez donner une note", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val comment = etComment.text.toString().trim()
                    submitRating(ride.id, rating, comment)
                }
                .setNegativeButton("Plus tard", null)
                .create()

            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage dialogue: ${e.message}", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitRating(rideId: Long, rating: Int, comment: String) {
        Log.d(TAG, "=== ENVOI AVIS ===")
        Log.d(TAG, "rideId: $rideId, rating: $rating, comment: $comment")

        val clientId = tokenManager.getUserId()
        Log.d(TAG, "clientId: $clientId")

        val request = ReviewRequest(
            rideId = rideId,
            clientId = clientId,
            rating = rating,
            comment = comment
        )

        Toast.makeText(this, "Envoi en cours...", Toast.LENGTH_SHORT).show()

        RetrofitClient.apiService.addReview(request).enqueue(object : Callback<ReviewResponse> {
            override fun onResponse(call: Call<ReviewResponse>, response: Response<ReviewResponse>) {
                Log.d(TAG, "Code HTTP: ${response.code()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "✅ Avis envoyé avec succès")
                    Toast.makeText(this@HistoryActivity, "⭐ Merci pour votre avis !", Toast.LENGTH_LONG).show()
                } else {
                    val errorMsg = response.body()?.message ?: "Erreur ${response.code()}"
                    Log.e(TAG, "❌ Erreur: $errorMsg")
                    Toast.makeText(this@HistoryActivity, "Erreur: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ReviewResponse>, t: Throwable) {
                Log.e(TAG, "❌ Échec: ${t.message}", t)
                Toast.makeText(this@HistoryActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteSelectedRides(rideIds: List<Long>) {
        Log.d(TAG, "=== SUPPRESSION COURSES ===")
        Log.d(TAG, "rideIds: $rideIds")

        Toast.makeText(this, "Suppression en cours...", Toast.LENGTH_SHORT).show()

        RetrofitClient.apiService.deleteMultipleClientRides(rideIds).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    val message = response.body() ?: "Courses supprimées"
                    Log.d(TAG, "✅ $message")
                    Toast.makeText(this@HistoryActivity, "✅ $message", Toast.LENGTH_SHORT).show()
                    loadHistory()
                } else {
                    Log.e(TAG, "❌ Erreur suppression: ${response.code()}")
                    Toast.makeText(this@HistoryActivity, "❌ Erreur lors de la suppression", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e(TAG, "❌ Échec suppression: ${t.message}")
                Toast.makeText(this@HistoryActivity, "❌ Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}