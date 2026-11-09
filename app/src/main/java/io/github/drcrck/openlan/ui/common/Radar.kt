package io.github.drcrck.openlan.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.drcrck.openlan.ui.theme.Brand
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar decorativo: anelli concentrici, un punto per dispositivo (posizione stabile ricavata dall'IP)
 * e un fascio che ruota durante la scansione.
 */
@Composable
fun Radar(points: List<Pair<String, Boolean>>, scanning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep",
    )
    val pulse by transition.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse",
    )

    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = center
        val ring = Color.White.copy(alpha = 0.18f)
        for (f in listOf(0.33f, 0.66f, 1f)) drawCircle(ring, radius = r * f - 1f, center = c, style = Stroke(2f))
        drawLine(ring, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.5f)
        drawLine(ring, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.5f)

        if (scanning) {
            rotate(angle, c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.75f to Color.Transparent,
                        1f to Brand.TealBright.copy(alpha = 0.55f), center = c,
                    ),
                    radius = r, center = c,
                )
            }
        }

        points.forEach { (ip, online) ->
            val h = ip.hashCode()
            val a = Math.toRadians(((h ushr 8) and 0x1FF) % 360.0)
            val d = r * (0.22f + ((h and 0xFF) / 255f) * 0.7f)
            val p = Offset(c.x + (cos(a) * d).toFloat(), c.y + (sin(a) * d).toFloat())
            val col = if (online) Brand.TealBright else Color.White.copy(alpha = 0.35f)
            if (online) drawCircle(col.copy(alpha = 0.25f * pulse), radius = 9f, center = p)
            drawCircle(col, radius = 4.5f, center = p)
        }
        drawCircle(Brand.Amber, radius = 6f, center = c)
    }
}
