package com.abdil.taxi

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.abdil.taxi.model.RegisterRequest
import com.abdil.taxi.network.RetrofitClient
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : BaseActivity() {

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tokenManager = TokenManager(this)

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)

        btnRegister.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (fullName.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
                Toast.makeText(this, getString(R.string.invalid_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            register(fullName, email, phone, password)
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun register(fullName: String, email: String, phone: String, password: String) {
        val request = RegisterRequest(fullName, email, phone, password)
        val call = RetrofitClient.authApiService.register(request)

        call.enqueue(object : Callback<com.abdil.taxi.model.AuthResponse> {
            override fun onResponse(call: Call<com.abdil.taxi.model.AuthResponse>, response: Response<com.abdil.taxi.model.AuthResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    if (authResponse.success) {
                        tokenManager.saveToken(authResponse.token ?: "")
                        authResponse.user?.let {
                            tokenManager.saveUserId(it.id)
                            tokenManager.saveUserEmail(it.email)
                            tokenManager.saveUserName(it.fullName)
                        }
                        Toast.makeText(this@RegisterActivity, authResponse.message ?: getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, authResponse.message ?: getString(R.string.register_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@RegisterActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<com.abdil.taxi.model.AuthResponse>, t: Throwable) {
                Toast.makeText(this@RegisterActivity, getString(R.string.error) + ": ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}