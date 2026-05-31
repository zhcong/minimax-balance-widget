package com.minimax.widget.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.minimax.widget.data.model.BalanceInfo
import com.minimax.widget.repository.MiniMaxRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiniMaxBalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = MiniMaxRepository(context)
        val balance = repository.getCachedBalance() ?: BalanceInfo.empty()

        provideContent {
            GlanceTheme {
                BalanceContent(balance)
            }
        }
    }
}

@Composable
private fun BalanceContent(balance: BalanceInfo) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(android.graphics.Color(0xFF000000)))
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "$ balance --minimax",
            style = TextStyle(
                color = ColorProvider(android.graphics.Color(0xFF00FF00)),
                fontSize = androidx.glance.unit.TextUnit(14f, androidx.glance.unit.TextUnitType.Sp),
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(android.graphics.Color(0xFF00FF00)))
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        // Balance
        Text(
            text = "Balance: ¥${String.format("%.2f", balance.balance)}",
            style = TextStyle(
                color = ColorProvider(android.graphics.Color(0xFF00FF00)),
                fontSize = androidx.glance.unit.TextUnit(16f, androidx.glance.unit.TextUnitType.Sp),
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        // Plan
        Text(
            text = "Plan: ${balance.planName}",
            style = TextStyle(
                color = ColorProvider(android.graphics.Color(0xFF00FF00)),
                fontSize = androidx.glance.unit.TextUnit(12f, androidx.glance.unit.TextUnitType.Sp)
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        // Expires
        Text(
            text = "Expires: ${balance.expiresAt}",
            style = TextStyle(
                color = ColorProvider(android.graphics.Color(0xFF888888)),
                fontSize = androidx.glance.unit.TextUnit(10f, androidx.glance.unit.TextUnitType.Sp)
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        // Last update
        val timeStr = if (balance.lastUpdate > 0) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(balance.lastUpdate))
        } else {
            "Never"
        }
        Text(
            text = "Last update: $timeStr",
            style = TextStyle(
                color = ColorProvider(android.graphics.Color(0xFF888888)),
                fontSize = androidx.glance.unit.TextUnit(10f, androidx.glance.unit.TextUnitType.Sp)
            )
        )
    }
}

class MiniMaxBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniMaxBalanceWidget()
}