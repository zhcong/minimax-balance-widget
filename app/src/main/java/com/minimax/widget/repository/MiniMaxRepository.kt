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

    data class ModelUsage(
        val name: String,
        val displayName: String,
        val used: Int,
        val total: Int,
        val resetTime: String
    ) {
        val remaining: Int get() = if (total > 0) total - used else 0
        val percentage: Int get() = if (total > 0) (used * 100 / total) else 0
        val hasQuota: Boolean get() = total > 0
    }

    data class BalanceResult(
        val usages: List<ModelUsage>,
        val lastUpdate: Long
    )

    suspend fun fetchBalance(): Result<BalanceResult> {
        return try {
            val apiKey = getApiKey() ?: return Result.failure(IllegalStateException("API Key not configured"))

            val request = Request.Builder()
                .url("https://www.minimaxi.com/v1/token_plan/remains")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))

            android.util.Log.d("MiniMaxAPI", "Response: $body")

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
                "MiniMax-M*" to "文本生成",
                "speech-hd" to "语音",
                "music-2.5" to "音乐 2.5",
                "music-2.6" to "音乐生成",
                "music-cover" to "音乐翻唱",
                "lyrics_generation" to "歌词生成",
                "image-01" to "图像生成",
                "MiniMax-Hailuo-2.3-Fast-6s-768p" to "海螺视频 Fast",
                "MiniMax-Hailuo-2.3-6s-768p" to "海螺视频 2.3",
                "coding-plan-vlm" to "图片理解 MCP",
                "coding-plan-search" to "网络搜索 MCP"
            )

            modelRemains?.forEach { model ->
                val modelName = model.asJsonObject.get("model_name")?.asString ?: ""
                val total = model.asJsonObject.get("current_interval_total_count")?.asInt ?: 0
                val used = model.asJsonObject.get("current_interval_usage_count")?.asInt ?: 0
                val resetTimestamp = model.asJsonObject.get("end_time")?.asLong ?: 0L

                val resetTime = if (resetTimestamp > 0) {
                    val resetDate = java.util.Date(resetTimestamp)
                    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    fmt.format(resetDate)
                } else {
                    "-"
                }

                usages.add(ModelUsage(
                    name = modelName,
                    displayName = displayNames[modelName] ?: modelName,
                    used = used,
                    total = total,
                    resetTime = resetTime
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