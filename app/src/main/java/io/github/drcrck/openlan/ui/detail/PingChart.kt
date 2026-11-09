package io.github.drcrck.openlan.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.ui.common.SectionCard
import io.github.drcrck.openlan.ui.theme.Mono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.scanner.PingStats
import java.util.Locale

private fun fmt(v: Double?) = v?.let { String.format(Locale.getDefault(), if (it < 10) "%.1f" else "%.0f", it) } ?: "–"

@OptIn(ExperimentalLayoutApi::class)
/** Grafico del ping in tempo reale: ultimi 60 secondi, perdite segnate in rosso. */
@Composable
fun PingChart(stats: PingStats, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lost = MaterialTheme.colorScheme.error

    SectionCard(
        modifier,
        title = "Ping",
        icon = Icons.Filled.Timeline,
        trailing = {
            Text(
                if (stats.sent == 0) stringResource(R.string.ping_starting)
                else if (stats.last == null) stringResource(R.string.ping_lost)
                else "${fmt(stats.last)} ms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = Mono,
                color = if (stats.sent > 0 && stats.last == null) lost else MaterialTheme.colorScheme.primary,
            )
        },
    ) {

            // Scala: il massimo visibile arrotondato, almeno 5 ms
            val visibleMax = stats.samples.filterNotNull().maxOrNull() ?: 0.0
            // Pavimento a 1 ms: su una LAN si leggono anche le variazioni sotto il millisecondo
            val scale = niceCeil(maxOf(visibleMax * 1.15, 1.0))

            Row {
                Text("${fmt(scale)} ms", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ping_window_stats, PingStats.WINDOW), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val summary = stringResource(
                R.string.ping_summary, fmt(stats.last), fmt(stats.min), fmt(stats.avg), fmt(stats.max), fmt(stats.jitter),
                String.format(Locale.getDefault(), "%.0f", stats.lossPercent),
            )
            Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = summary }) {
                val w = size.width
                val h = size.height
                val step = w / (PingStats.WINDOW - 1)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                for (f in listOf(0f, 0.5f, 1f)) {
                    drawLine(grid, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1f, pathEffect = if (f == 1f) null else dash)
                }
                // I campioni sono allineati a destra: il più recente sul bordo destro
                val offset = PingStats.WINDOW - stats.samples.size
                var path: Path? = null
                stats.samples.forEachIndexed { i, v ->
                    val x = (offset + i) * step
                    if (v == null) {
                        drawLine(lost, Offset(x, h), Offset(x, h * 0.75f), strokeWidth = 3f)
                        path?.let { drawPath(it, line, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                        path = null
                    } else {
                        val y = h - (v / scale * h).toFloat().coerceIn(0f, h)
                        val p = path
                        if (p == null) path = Path().apply { moveTo(x, y) } else p.lineTo(x, y)
                        drawCircle(line, radius = 3f, center = Offset(x, y))
                    }
                }
                path?.let { drawPath(it, line, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("min", "${fmt(stats.min)} ms")
                Stat(stringResource(R.string.ping_avg), "${fmt(stats.avg)} ms")
                Stat("max", "${fmt(stats.max)} ms")
                Stat("jitter", "${fmt(stats.jitter)} ms")
                Stat(stringResource(R.string.ping_loss), String.format(Locale.getDefault(), "%.0f%% (%d/%d)", stats.lossPercent, stats.sent - stats.received, stats.sent))
            }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
    }
}

/** Arrotonda a 1-2-5 × 10^n per una scala leggibile. */
private fun niceCeil(v: Double): Double {
    val exp = Math.pow(10.0, Math.floor(Math.log10(v)))
    val f = v / exp
    val n = when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    }
    return n * exp
}
