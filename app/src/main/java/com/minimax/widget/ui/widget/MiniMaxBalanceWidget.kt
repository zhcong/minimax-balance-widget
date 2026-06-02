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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.Alignment
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
                BalanceContent(usages)
            }
        }
    }

    private fun parseUsages(json: String): List<MiniMaxRepository.ModelUsage> {
        return try {
            val type = object : TypeToken<List<MiniMaxRepository.ModelUsage>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private data class QuotaSummary(
    val used: Int,
    val total: Int,
    val remainingPercent: Int
) {
    val percentage: Int
        get() = if (total > 0) (used * 100 / total) else (100 - remainingPercent)
}

private fun aggregateInterval(usages: List<MiniMaxRepository.ModelUsage>): QuotaSummary {
    val used = usages.sumOf { it.intervalUsed }
    val total = usages.sumOf { it.intervalTotal }
    val remaining = usages.minOfOrNull { it.intervalRemainingPercent } ?: 100
    return QuotaSummary(used, total, remaining)
}

private fun aggregateWeekly(usages: List<MiniMaxRepository.ModelUsage>): QuotaSummary {
    val used = usages.sumOf { it.weeklyUsed }
    val total = usages.sumOf { it.weeklyTotal }
    val remaining = usages.minOfOrNull { it.weeklyRemainingPercent } ?: 100
    return QuotaSummary(used, total, remaining)
}

@Composable
private fun QuotaBlock(
    isLast: Boolean,
    label: String,
    summary: QuotaSummary,
    labelColor: ColorProvider,
    barColor: ColorProvider,
    grayColor: ColorProvider
) {
    val branch = if (isLast) "└─ " else "├─ "
    val linePrefix = "│  "
    val barWidth = 12
    val filled = if (summary.percentage in 1..99) summary.percentage * barWidth / 100
                 else if (summary.percentage >= 99) barWidth else 0
    val bar = "${"█".repeat(filled)}${"░".repeat(barWidth - filled)}"
    val detail = if (summary.total > 0) " ${summary.used}/${summary.total}" else ""

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "$branch$label",
                style = TextStyle(
                    color = labelColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${summary.percentage}%$detail",
                style = TextStyle(
                    color = barColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
        Text(
            text = "$linePrefix$bar",
            style = TextStyle(
                color = barColor,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

@Composable
private fun BalanceContent(usages: List<MiniMaxRepository.ModelUsage>) {
    val bg = Color(0xFF1A1E2E)
    val blue = Color(0xFF5699FF)
    val gray = Color(0xFF4A5A78)
    val white = Color(0xFFC8D6E5)

    val interval = aggregateInterval(usages)
    val weekly = aggregateWeekly(usages)
    val resetTime = usages.firstOrNull()?.resetTime ?: "-"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bg))
            .clickable(actionStartActivity<RefreshActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(
                text = "$ minimax",
                style = TextStyle(
                    color = ColorProvider(blue),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            QuotaBlock(
                isLast = false,
                label = "5h 限额",
                summary = interval,
                labelColor = ColorProvider(white),
                barColor = ColorProvider(blue),
                grayColor = ColorProvider(gray)
            )
            QuotaBlock(
                isLast = true,
                label = "周限额",
                summary = weekly,
                labelColor = ColorProvider(white),
                barColor = ColorProvider(blue),
                grayColor = ColorProvider(gray)
            )
            Text(
                text = "$ _ reset $resetTime",
                style = TextStyle(
                    color = ColorProvider(gray),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

class MiniMaxBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniMaxBalanceWidget()
}
