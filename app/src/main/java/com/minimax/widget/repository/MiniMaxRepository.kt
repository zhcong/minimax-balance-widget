package com.minimax.widget.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.minimax.widget.data.model.BalanceInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    data class ModelUsage(
        val name: String,
        val displayName: String,
        val intervalUsed: Int,
        val intervalTotal: Int,
        val weeklyUsed: Int,
        val weeklyTotal: Int,
        val resetTime: String,
        val intervalRemainingPercent: Int,
        val weeklyRemainingPercent: Int
    ) {
        val intervalPercentage: Int
            get() = if (intervalTotal > 0) (intervalUsed * 100 / intervalTotal) else 0
        val weeklyPercentage: Int
            get() = if (weeklyTotal > 0) (weeklyUsed * 100 / weeklyTotal) else 0
        val hasIntervalQuota: Boolean get() = intervalTotal > 0
        val hasWeeklyQuota: Boolean get() = weeklyTotal > 0
    }

    data class BalanceResult(
        val usages: List<ModelUsage>,
        val lastUpdate: Long
    )

    suspend fun fetchBalance(): Result<BalanceResult> {
        return try {
            val apiKey = getApiKey() ?: return Result.failure(IllegalStateException("API Key not configured"))

            val request = Request.Builder()
                .url("https://www.minimaxi.com/v1/api/openplatform/coding_plan/remains")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))

            val json = com.google.gson.JsonParser.parseString(body).asJsonObject

            val baseResp = json.getAsJsonObject("base_resp")
            if (baseResp != null) {
                val statusCode = baseResp.get("status_code")?.asInt ?: -1
                if (statusCode != 0) {
                    val msg = baseResp.get("status_msg")?.asString ?: "Unknown error"
                    return Result.failure(IllegalStateException(msg))
                }
            }

            val modelRemains = json.getAsJsonArray("model_remains")
            val usages = mutableListOf<ModelUsage>()

            val displayNames = mapOf(
                "general" to "通用",
                "video" to "视频"
            )

            modelRemains?.forEach { model ->
                val obj = model.asJsonObject
                val modelName = obj.get("model_name")?.asString ?: ""

                val intervalTotal = obj.get("current_interval_total_count")?.asInt ?: 0
                val intervalUsed = obj.get("current_interval_usage_count")?.asInt ?: 0
                val intervalRemainingPercent = obj.get("current_interval_remaining_percent")?.asInt ?: 100

                val weeklyTotal = obj.get("current_weekly_total_count")?.asInt ?: 0
                val weeklyUsed = obj.get("current_weekly_usage_count")?.asInt ?: 0
                val weeklyRemainingPercent = obj.get("current_weekly_remaining_percent")?.asInt ?: 100

                val resetTimestamp = obj.get("end_time")?.asLong ?: 0L
                val resetTime = if (resetTimestamp > 0) {
                    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    fmt.format(Date(resetTimestamp))
                } else {
                    "-"
                }

                usages.add(ModelUsage(
                    name = modelName,
                    displayName = displayNames[modelName] ?: modelName,
                    intervalUsed = intervalUsed,
                    intervalTotal = intervalTotal,
                    weeklyUsed = weeklyUsed,
                    weeklyTotal = weeklyTotal,
                    resetTime = resetTime,
                    intervalRemainingPercent = intervalRemainingPercent,
                    weeklyRemainingPercent = weeklyRemainingPercent
                ))
            }

            Result.success(BalanceResult(usages, System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedBalance(): BalanceInfo? {
        val json = prefs.getString("cached_balance", null) ?: return null
        return BalanceInfo.fromJson(json)
    }

    fun saveCachedBalance(balance: BalanceInfo) {
        prefs.edit().putString("cached_balance", balance.toJson()).apply()
    }

    fun getSelectedModels(): Set<String> {
        return prefs.getStringSet("selected_models", emptySet()) ?: emptySet()
    }

    fun saveSelectedModels(models: List<String>) {
        prefs.edit().putStringSet("selected_models", models.toSet()).apply()
    }
}
