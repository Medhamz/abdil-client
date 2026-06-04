package com.abdil.taxi

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.abdil.taxi.model.AuthRequest
import com.abdil.taxi.model.AuthResponse
import com.abdil.taxi.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : BaseActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvRegister: TextView
    private lateinit var spinnerLanguage: Spinner
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tokenManager = TokenManager(this)

        if (tokenManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)

        setupLanguageSpinner()

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.invalid_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            login(email, password)
        }

        // ✅ Un seul click listener sur tvRegister
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
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

        spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedCode = codes[position]
                val currentLang = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "fr")
                if (selectedCode != currentLang) {
                    updateLanguage(selectedCode)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun login(email: String, password: String) {
        val request = AuthRequest(email, password)
        val call = RetrofitClient.authApiService.login(request)

        call.enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    if (authResponse.success) {
                        val user = authResponse.user
                        if (user != null) {
                            tokenManager.saveUser(
                                userId = user.id,
                                userName = user.fullName,
                                userEmail = user.email,
                                token = authResponse.token ?: ""
                            )
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, getString(R.string.login_error), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, authResponse.message ?: getString(R.string.login_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, getString(R.string.login_error), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                Toast.makeText(this@LoginActivity, "${getString(R.string.error)}: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}