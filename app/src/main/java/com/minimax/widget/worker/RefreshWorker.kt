package com.minimax.widget.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minimax.widget.repository.MiniMaxRepository
import com.minimax.widget.ui.widget.MiniMaxBalanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = MiniMaxRepository(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val result = repository.fetchBalance()
            result.onSuccess { balance ->
                repository.saveCachedBalance(balance)
                MiniMaxBalanceWidget().updateAll(
                    GlanceAppWidgetManager(applicationContext)
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "minimax_balance_refresh"
    }
}