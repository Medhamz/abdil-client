package com.abdil.taxi

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialiser Firebase
        FirebaseApp.initializeApp(this)
        // Configurer Storage
        FirebaseStorage.getInstance().setMaxUploadRetryTimeMillis(30000)
    }
}