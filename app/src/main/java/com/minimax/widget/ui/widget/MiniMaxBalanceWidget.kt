package com.minimax.widget.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.minimax.widget.data.model.BalanceInfo
import com.minimax.widget.repository.MiniMaxRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

class MiniMaxBalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = MiniMaxRepository(context)
        val balance = repository.getCachedBalance() ?: BalanceInfo.empty()
        val allUsages = parseUsages(balance.usagesJson)
        val selectedModels = repository.getSelectedModels()
        val usages = if (selectedModels.isEmpty()) allUsages
                      else allUsages.filter { it.name in selectedModels }

        provideContent {
            GlanceTheme {
                BalanceContent(balance, usages)
            }
        }
    }

    private fun parseUsages(json: String): List<ModelUsage> {
        return try {
            val type = object : TypeToken<List<ModelUsage>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Composable
private fun BalanceContent(balance: BalanceInfo, usages: List<ModelUsage>) {
    val bg = Color(0xFF0D0D0D)
    val green = Color(0xFF00FF41)
    val gray = Color(0xFF556650)
    val white = Color(0xFFCCDDCC)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bg))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$ minimax",
            style = TextStyle(
                color = ColorProvider(green),
                fontSize = 10.sp
            )
        )

        Spacer(modifier = GlanceModifier.height(3.dp))

        usages.forEach { usage ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "  ${usage.displayName}",
                        style = TextStyle(
                            color = ColorProvider(white),
                            fontSize = 9.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "${usage.percentage}%",
                        style = TextStyle(
                            color = ColorProvider(green),
                            fontSize = 9.sp
                        )
                    )
                }
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    val filled = usage.percentage * 16 / 100
                    Text(
                        text = "  ${"█".repeat(filled)}${"░".repeat(16 - filled)}",
                        style = TextStyle(
                            color = ColorProvider(green),
                            fontSize = 7.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "${usage.used}/${usage.total}",
                        style = TextStyle(
                            color = ColorProvider(gray),
                            fontSize = 7.sp
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
        }

        Text(
            text = "$ _ ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(balance.lastUpdate))}",
            style = TextStyle(
                color = ColorProvider(gray),
                fontSize = 7.sp
            )
        )
    }
}

class MiniMaxBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniMaxBalanceWidget()
}