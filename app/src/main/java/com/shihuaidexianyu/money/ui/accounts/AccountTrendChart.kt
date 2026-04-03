package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val axisFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val settlementMarkerColor = Color(0xFFB45309)
private val currentMarkerColor = Color(0xFF0F766E)

@Composable
fun AccountTrendChartCard(
    chart: AccountTrendChartUiModel,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("余额走势", style = MaterialTheme.typography.titleMedium)
                if (!chart.hasData) {
                    Text("还没有足够的余额轨迹", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "最高 ${AmountFormatter.format(chart.rawMaxBalance, settings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            "最低 ${AmountFormatter.format(chart.rawMinBalance, settings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    AccountTrendCanvas(chart = chart)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            chart.startAt?.let(::formatAxisTime) ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            chart.endAt?.let(::formatAxisTime) ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TrendLegend(chart = chart)
                }
            }
        }
    }
}

@Composable
private fun AccountTrendCanvas(
    chart: AccountTrendChartUiModel,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (chart.points.isEmpty()) return@Canvas

        val left = 16.dp.toPx()
        val top = 12.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return@Canvas

        val yRange = (chart.paddedMaxBalance - chart.paddedMinBalance).takeIf { it > 0.0 } ?: 1.0
        val firstTime = chart.points.first().timeMillis
        val lastTime = chart.points.last().timeMillis
        val timeRange = (lastTime - firstTime).takeIf { it > 0L }

        repeat(3) { index ->
            val y = top + height * (index / 2f)
            drawLine(
                color = grid.copy(alpha = 0.55f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        fun xFor(index: Int, timeMillis: Long): Float {
            return when {
                chart.points.size == 1 -> left + width / 2f
                timeRange == null -> {
                    val step = width / max(chart.points.lastIndex, 1)
                    left + step * index
                }
                else -> left + width * ((timeMillis - firstTime).toDouble() / timeRange.toDouble()).toFloat()
            }
        }

        fun yFor(balance: Long): Float {
            val ratio = ((balance - chart.paddedMinBalance) / yRange).toFloat()
            return bottom - height * ratio.coerceIn(0f, 1f)
        }

        val path = Path()
        chart.points.forEachIndexed { index, point ->
            val x = xFor(index, point.timeMillis)
            val y = yFor(point.balance)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = primary,
            style = Stroke(width = 3.dp.toPx()),
        )

        chart.points.forEachIndexed { index, point ->
            val x = xFor(index, point.timeMillis)
            val y = yFor(point.balance)
            val color = when (point.markerType) {
                AccountTrendMarkerType.INITIAL -> outline
                AccountTrendMarkerType.UPDATE -> secondary
                AccountTrendMarkerType.SETTLEMENT -> settlementMarkerColor
                AccountTrendMarkerType.CURRENT -> currentMarkerColor
            }
            val radius = when (point.markerType) {
                AccountTrendMarkerType.SETTLEMENT -> 5.dp.toPx()
                AccountTrendMarkerType.CURRENT -> 4.5.dp.toPx()
                else -> 4.dp.toPx()
            }
            drawCircle(color = Color.White, radius = radius + 1.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = color, radius = radius, center = Offset(x, y))
        }
    }
}

@Composable
private fun TrendLegend(
    chart: AccountTrendChartUiModel,
    modifier: Modifier = Modifier,
) {
    val legendItems = buildList {
        add(AccountTrendMarkerType.INITIAL to "初始余额")
        if (chart.points.any { it.markerType == AccountTrendMarkerType.UPDATE }) {
            add(AccountTrendMarkerType.UPDATE to "更新余额")
        }
        if (chart.points.any { it.markerType == AccountTrendMarkerType.SETTLEMENT }) {
            add(AccountTrendMarkerType.SETTLEMENT to "结算点")
        }
        add(AccountTrendMarkerType.CURRENT to "当前余额")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        legendItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colorForMarker(type), CircleShape),
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun colorForMarker(type: AccountTrendMarkerType): Color {
    return when (type) {
        AccountTrendMarkerType.INITIAL -> MaterialTheme.colorScheme.outline
        AccountTrendMarkerType.UPDATE -> MaterialTheme.colorScheme.secondary
        AccountTrendMarkerType.SETTLEMENT -> settlementMarkerColor
        AccountTrendMarkerType.CURRENT -> currentMarkerColor
    }
}

private fun formatAxisTime(timeMillis: Long): String {
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(axisFormatter)
}

