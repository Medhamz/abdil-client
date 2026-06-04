package com.abdil.taxi

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tokenManager = TokenManager(this)
        tokenManager.logout()

        val ivLogo = findViewById<ImageView>(R.id.ivLogo)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)
        val viewLine = findViewById<View>(R.id.viewLine)
        val tvSlogan = findViewById<TextView>(R.id.tvSlogan)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        ivLogo.animate().alpha(1f).setDuration(500).start()
        Handler(Looper.getMainLooper()).postDelayed({ tvTitle.animate().alpha(1f).setDuration(500).start() }, 200)
        Handler(Looper.getMainLooper()).postDelayed({ tvSubtitle.animate().alpha(1f).setDuration(500).start() }, 400)
        Handler(Looper.getMainLooper()).postDelayed({ viewLine.animate().alpha(1f).setDuration(500).start() }, 600)
        Handler(Looper.getMainLooper()).postDelayed({ tvSlogan.animate().alpha(1f).setDuration(500).start() }, 800)
        Handler(Looper.getMainLooper()).postDelayed({ progressBar.animate().alpha(1f).setDuration(500).start() }, 1000)

        // ✅ VÉRIFIER LA VERSION DE L'APPLICATION
        checkAppVersion()
    }

    private fun checkAppVersion() {
        val currentVersionCode = getCurrentVersionCode()

        RetrofitClient.apiService.getClientVersion().enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val minVersionCode = (data["minVersionCode"] as? Number)?.toInt() ?: 1
                    val forceUpdate = data["forceUpdate"] as? Boolean ?: false
                    val updateUrl = data["updateUrl"] as? String ?: ""
                    val message = data["message"] as? String ?: "Mise à jour disponible"

                    if (currentVersionCode < minVersionCode) {
                        showUpdateDialog(forceUpdate, updateUrl, message)
                    } else {
                        goToNextScreen()
                    }
                } else {
                    goToNextScreen()
                }
            }

            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                goToNextScreen()
            }
        })
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }

    private fun showUpdateDialog(forceUpdate: Boolean, updateUrl: String, message: String) {
        val builder = AlertDialog.Builder(this)
            .setTitle("📱 Mise à jour disponible")
            .setMessage(message)
            .setPositiveButton("Mettre à jour") { _, _ ->
                // Ouvrir le Play Store
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Veuillez mettre à jour depuis le Play Store", Toast.LENGTH_LONG).show()
                }
            }

        if (!forceUpdate) {
            builder.setNegativeButton("Plus tard") { _, _ ->
                goToNextScreen()
            }
        } else {
            builder.setCancelable(false)
        }

        builder.show()
    }

    private fun goToNextScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}