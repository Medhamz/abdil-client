package com.abdil.taxi

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
        val language = prefs.getString("language", "fr") ?: "fr"
        val context = LanguageHelper.setLocale(newBase, language)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguage()
    }

    private fun applyLanguage() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val language = prefs.getString("language", "fr") ?: "fr"
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun updateLanguage(languageCode: String) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("language", languageCode).apply()
        recreate()
    }
}