package com.abdil.taxi

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirebaseConfig {
    private const val TAG = "FirebaseConfig"

    fun initializeFirestore() {
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            db.firestoreSettings = settings
            Log.d(TAG, "✅ Firestore initialisé")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur: ${e.message}")
        }
    }
}