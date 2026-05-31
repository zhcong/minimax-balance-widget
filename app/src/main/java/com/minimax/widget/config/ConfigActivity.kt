package com.minimax.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.minimax.widget.databinding.ActivityConfigBinding
import com.minimax.widget.repository.MiniMaxRepository
import com.minimax.widget.ui.widget.MiniMaxBalanceWidget
import com.minimax.widget.worker.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private val repository by lazy { MiniMaxRepository(this) }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        // Auto-refresh on startup if API key exists
        val existingKey = repository.getApiKey()
        if (existingKey != null) {
            binding.apiKeyInput.setText(existingKey)
            fetchAndUpdateUI(existingKey)
        }

        binding.saveButton.setOnClickListener {
            val apiKey = binding.apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repository.saveApiKey(apiKey)
            fetchAndUpdateUI(apiKey)
        }

        binding.saveDisplayButton.setOnClickListener {
            saveSelectionAndUpdateWidget()
        }
    }

    private fun restoreCheckboxesFromCache() {
        val cached = repository.getCachedBalance()
        val selected = repository.getSelectedModels()
        if (cached != null) {
            val usages = parseCachedUsages(cached.usagesJson)
            if (usages.isNotEmpty()) {
                showModelCheckboxes(usages, selected)
            }
        }
    }

    private fun parseCachedUsages(json: String): List<MiniMaxRepository.ModelUsage> {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<MiniMaxRepository.ModelUsage>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchAndUpdateUI(apiKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.fetchBalance()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val balanceResult = result.getOrNull()
                    if (balanceResult != null) {
                        val balance = com.minimax.widget.data.model.BalanceInfo(
                            balance = balanceResult.usages.sumOf { it.remaining }.toDouble(),
                            planName = "MiniMax Code Plan",
                            expiresAt = "",
                            lastUpdate = balanceResult.lastUpdate,
                            usagesJson = Gson().toJson(balanceResult.usages)
                        )
                        repository.saveCachedBalance(balance)

                        val msg = buildString {
                            append("✓ Fetched:\n")
                            balanceResult.usages.forEach { u ->
                                if (u.hasQuota) {
                                    append("  ${u.displayName}: ${u.used}/${u.total}\n")
                                }
                            }
                        }
                        binding.statusText.text = msg.trimEnd()
                        binding.statusText.visibility = android.view.View.VISIBLE

                        val selected = repository.getSelectedModels()
                        showModelCheckboxes(balanceResult.usages, selected)
                        scheduleRefresh()

                        // Update widget immediately
                        CoroutineScope(Dispatchers.Main).launch {
                            MiniMaxBalanceWidget().updateAll(this@ConfigActivity)
                        }

                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    binding.statusText.text = "✗ Error: $error"
                    binding.statusText.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun showModelCheckboxes(
        usages: List<MiniMaxRepository.ModelUsage>,
        selectedModels: Set<String>
    ) {
        binding.modelLabel.visibility = android.view.View.VISIBLE
        binding.modelCheckboxContainer.removeAllViews()

        usages.forEach { usage ->
            val cb = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
                text = "${usage.displayName} (${usage.used}/${usage.total})"
                setTextColor(android.graphics.Color.parseColor("#CCDDCC"))
                textSize = 14f
                isChecked = selectedModels.isEmpty() || selectedModels.contains(usage.name)
                tag = usage.name
            }
            binding.modelCheckboxContainer.addView(cb)
        }

        binding.modelCheckboxContainer.visibility = android.view.View.VISIBLE
        binding.saveDisplayButton.visibility = android.view.View.VISIBLE
    }

    private fun saveSelectionAndUpdateWidget() {
        val selected = mutableListOf<String>()
        for (i in 0 until binding.modelCheckboxContainer.childCount) {
            val cb = binding.modelCheckboxContainer.getChildAt(i) as? com.google.android.material.checkbox.MaterialCheckBox
            if (cb?.isChecked == true) {
                cb.tag?.toString()?.let { selected.add(it) }
            }
        }
        repository.saveSelectedModels(selected)
        Toast.makeText(this, "Widget updated!", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            MiniMaxBalanceWidget().updateAll(this@ConfigActivity)
        }

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private fun scheduleRefresh() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(false)
            .build()
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<RefreshWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RefreshWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        // Also trigger immediate one-time refresh
        val oneShot = androidx.work.OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "minimax_balance_refresh_immediate",
            androidx.work.ExistingWorkPolicy.REPLACE,
            oneShot
        )
    }
}