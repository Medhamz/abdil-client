package com.abdil.taxi

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("abdil_taxi_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("JWT_TOKEN", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("JWT_TOKEN", null)
    }

    fun saveUserId(userId: Long) {
        prefs.edit().putLong("USER_ID", userId).apply()
    }

    fun getUserId(): Long {
        return prefs.getLong("USER_ID", -1)
    }

    fun saveUserEmail(email: String) {
        prefs.edit().putString("USER_EMAIL", email).apply()
    }

    fun getUserEmail(): String? {
        return prefs.getString("USER_EMAIL", null)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString("USER_NAME", name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString("USER_NAME", null)
    }

    // ✅ MÉTHODE POUR RÉCUPÉRER LE NOM DU CLIENT (alias)
    fun getClientName(): String? {
        return getUserName()
    }

    // ✅ MÉTHODE POUR RÉCUPÉRER LE TÉLÉPHONE DU CLIENT
    fun getClientPhone(): String? {
        return getUserEmail() // ou une clé séparée si vous avez "USER_PHONE"
    }

    fun saveFcmToken(token: String) {
        prefs.edit().putString("FCM_TOKEN", token).apply()
    }

    fun getFcmToken(): String? {
        return prefs.getString("FCM_TOKEN", null)
    }

    // Sauvegarde complète de l'utilisateur
    fun saveUser(userId: Long, userName: String, userEmail: String, token: String) {
        prefs.edit().apply {
            putLong("USER_ID", userId)
            putString("USER_NAME", userName)
            putString("USER_EMAIL", userEmail)
            putString("JWT_TOKEN", token)
            apply()
        }
    }

    // ✅ SAUVEGARDE AVEC TÉLÉPHONE
    fun saveUserWithPhone(userId: Long, userName: String, phone: String, token: String) {
        prefs.edit().apply {
            putLong("USER_ID", userId)
            putString("USER_NAME", userName)
            putString("USER_PHONE", phone)
            putString("JWT_TOKEN", token)
            apply()
        }
    }

    // ✅ RÉCUPÉRER LE TÉLÉPHONE
    fun getUserPhone(): String? {
        return prefs.getString("USER_PHONE", null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null && getUserId() != -1L
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}