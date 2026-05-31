package com.minimax.widget.config

import android.app.Activity
import android.os.Bundle
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.minimax.widget.ui.widget.MiniMaxBalanceWidget
import com.minimax.widget.worker.RefreshWorker
import java.util.concurrent.TimeUnit

class RefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(false)
            .build()
        val work = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "minimax_balance_refresh_immediate",
            ExistingWorkPolicy.REPLACE,
            work
        )

        finish()
    }
}
