package com.minimax.widget.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.minimax.widget.data.model.BalanceInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MiniMaxRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "minimax_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(): String? = prefs.getString("api_key", null)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("api_key", apiKey).apply()
    }

    suspend fun fetchBalance(): Result<BalanceInfo> = runCatching {
        val apiKey = getApiKey() ?: throw IllegalStateException("API Key not configured")

        val request = Request.Builder()
            .url("https://api.minimax.chat/v1/balance?api_key=$apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")

        val json = com.google.gson.JsonParser.parseString(body).asJsonObject

        val balance = json.get("balance")?.asDouble ?: 0.0
        val planName = json.get("plan_name")?.asString ?: "Unknown"
        val expiresAt = json.get("expires_at")?.asString ?: "N/A"

        BalanceInfo(
            balance = balance,
            planName = planName,
            expiresAt = expiresAt,
            lastUpdate = System.currentTimeMillis()
        )
    }

    fun getCachedBalance(): BalanceInfo? {
        val json = prefs.getString("cached_balance", null) ?: return null
        return BalanceInfo.fromJson(json)
    }

    fun saveCachedBalance(balance: BalanceInfo) {
        prefs.edit().putString("cached_balance", balance.toJson()).apply()
    }
}