package app.ryzik.chat.ui.flux

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.random.Random

/** Конфетти поверх всего приложения: при подарке и когда подарок приходит. */
object Confetti {
    private val _bursts = MutableStateFlow(0)
    val bursts = _bursts.asStateFlow()
    fun fire() { _bursts.value++ }
}

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val round: Boolean, val phase: Float)

private val ConfettiColors = listOf(
    Color(0xFFFFB443), Color(0xFFFF6B6B), Color(0xFF9C4DFF), Color(0xFF3D8BFF), Color(0xFF2FBF71), Color(0xFFFF4F8B), Color(0xFFFFE066),
)

@Composable
fun ConfettiOverlay() {
    val burst by Confetti.bursts.collectAsState()
    if (burst == 0) return
    val progress = remember(burst) { Animatable(0f) }
    val pieces = remember(burst) {
        val r = Random(burst * 7919L)
        List(140) {
            Piece(
                x = r.nextFloat(),
                vx = (r.nextFloat() - 0.5f) * 0.5f,
                vy = 0.15f + r.nextFloat() * 0.9f,
                spin = (r.nextFloat() - 0.5f) * 1440f,
                color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                w = 8f + r.nextFloat() * 10f,
                h = 5f + r.nextFloat() * 8f,
                round = r.nextInt(4) == 0,
                phase = r.nextFloat() * 6.28f,
            )
        }
    }
    LaunchedEffect(burst) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val fade = if (t > 0.75f) (1f - t) / 0.25f else 1f
        pieces.forEach { p ->
            // Выстрел вверх из нижней части экрана, потом падение с покачиванием.
            val y = size.height * (1.05f - p.vy * 1.6f * t + 1.9f * t * t)
            val x = size.width * (p.x + p.vx * t) + sin(t * 12f + p.phase) * 18f
            val c = p.color.copy(alpha = fade)
            rotate(p.spin * t, Offset(x, y)) {
                if (p.round) drawCircle(c, p.w / 2.2f, Offset(x, y))
                else drawRect(c, Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h * (0.4f + 0.6f * kotlin.math.abs(sin(t * 9f + p.phase)))))
            }
        }
    }
}
