package com.minimax.widget.config

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveButton.setOnClickListener {
            val apiKey = binding.apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repository.saveApiKey(apiKey)

            CoroutineScope(Dispatchers.IO).launch {
                val result = repository.fetchBalance()
                result.onSuccess { balance ->
                    repository.saveCachedBalance(balance)
                    MiniMaxBalanceWidget().updateAll(
                        GlanceAppWidgetManager(this@ConfigActivity)
                    )
                    scheduleRefresh()
                }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@ConfigActivity, "Saved! Widget will update shortly.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ConfigActivity, "API Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun scheduleRefresh() {
        val workRequest = PeriodicWorkRequestBuilder<RefreshWorker>(5, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}