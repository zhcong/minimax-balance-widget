package com.minimax.widget.data.model

data class BalanceInfo(
    val balance: Double,
    val planName: String,
    val expiresAt: String,
    val lastUpdate: Long = System.currentTimeMillis(),
    val usagesJson: String = "[]"
) {
    fun toJson(): String = com.google.gson.Gson().toJson(this)

    companion object {
        fun fromJson(json: String): BalanceInfo? = try {
            com.google.gson.Gson().fromJson(json, BalanceInfo::class.java)
        } catch (e: Exception) {
            null
        }

        fun empty() = BalanceInfo(
            balance = 0.0,
            planName = "Unknown",
            expiresAt = "N/A",
            lastUpdate = 0L,
            usagesJson = "[]"
        )
    }
}