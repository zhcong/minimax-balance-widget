package com.minimax.widget.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.text.Text
import androidx.glance.text.FontFamily
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.minimax.widget.data.model.BalanceInfo
import com.minimax.widget.config.RefreshActivity
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
    val bg = Color(0xFF1A1E2E)
    val blue = Color(0xFF5699FF)
    val gray = Color(0xFF4A5A78)
    val white = Color(0xFFC8D6E5)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bg))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<RefreshActivity>())
    ) {
        Text(
            text = "$ minimax",
            style = TextStyle(
                color = ColorProvider(blue),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        usages.forEachIndexed { i, usage ->
            val branch = if (i == usages.lastIndex) "└─ " else "├─ "
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "$branch${usage.displayName}",
                        style = TextStyle(
                            color = ColorProvider(white),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "${usage.percentage}%",
                        style = TextStyle(
                            color = ColorProvider(blue),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    val filled = if (usage.total > 0 && usage.used > 0)
                        (usage.used * 8 / usage.total).coerceAtLeast(1) else 0
                    Text(
                        text = "│ ${"█".repeat(filled)}${"░".repeat(8 - filled)}",
                        style = TextStyle(
                            color = ColorProvider(blue),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "${usage.used}/${usage.total}",
                        style = TextStyle(
                            color = ColorProvider(gray),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(1.dp))

        val resetDisplay = usages.firstOrNull()?.resetTime
            ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(balance.lastUpdate))

        Text(
            text = "$ _ reset $resetDisplay",
            style = TextStyle(
                color = ColorProvider(gray),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

class MiniMaxBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniMaxBalanceWidget()
}