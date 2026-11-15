package app.ryzik.chat.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import app.ryzik.chat.data.SeasonStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Узорные обои в духе Telegram: снежинки, телефоны, пузыри, самолётики, сердца. */
@Composable
fun WallpaperBackground(index: Int, modifier: Modifier = Modifier) {
    val colors = wallpaperColors(index)
    val season = LocalSeason.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = if (dark) Color.White.copy(alpha = 0.11f) else Color(0xFF0B2038).copy(alpha = 0.09f)
    val doodles = remember(index, season) { makeDoodles(index, season) }
    Box(modifier.background(Brush.verticalGradient(colors))) {
        if (doodles.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                for (p in doodles) {
                    rotate(p.rot, Offset(p.x * w, p.y * h)) {
                        drawDoodle(p.kind, p.x * w, p.y * h, p.s * w, ink)
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperPreview(index: Int, modifier: Modifier = Modifier) = WallpaperBackground(index, modifier)

private class Doodle(val x: Float, val y: Float, val s: Float, val rot: Float, val kind: Int)

private const val PHONE = 0
private const val BUBBLE = 1
private const val PLANE = 2
private const val HEART = 3
private const val STAR = 4
private const val SNOW = 5
private const val LEAF = 6
private const val WAVE = 7
private const val BUILDING = 8
private const val PLANET = 9
private const val TREE = 10
private const val SPARK = 11

private fun makeDoodles(index: Int, season: SeasonStyle): List<Doodle> {
    val kinds = when (index) {
        0 -> when (season) {
            SeasonStyle.Winter -> intArrayOf(SNOW, PHONE, BUBBLE, HEART, PLANE, STAR, SPARK)
            SeasonStyle.Halloween -> intArrayOf(LEAF, STAR, SPARK, PLANET)
            SeasonStyle.NewYear -> intArrayOf(SNOW, TREE, STAR, SPARK)
            SeasonStyle.None -> return emptyList()
        }
        1 -> intArrayOf(STAR, HEART, PLANE, SPARK)
        2 -> intArrayOf(BUBBLE, PHONE, LEAF)
        3 -> intArrayOf(WAVE, STAR, PLANE, SPARK)
        4 -> intArrayOf(PHONE, BUBBLE, HEART)
        5 -> intArrayOf(SNOW, STAR, SPARK)
        6 -> intArrayOf(SNOW, SNOW, STAR)
        7 -> intArrayOf(WAVE, STAR, PLANET)
        8 -> intArrayOf(SNOW, SPARK, STAR)
        9 -> intArrayOf(STAR, PLANET, SPARK)
        10 -> intArrayOf(TREE, SNOW, STAR)
        11 -> intArrayOf(STAR, SPARK, PLANET)
        12 -> intArrayOf(PHONE, PHONE, BUBBLE, SPARK)
        13 -> intArrayOf(BUBBLE, PLANE, HEART, STAR, PHONE)
        14 -> intArrayOf(BUILDING, STAR, SPARK)
        15 -> intArrayOf(PLANET, STAR, SPARK, WAVE)
        16 -> intArrayOf(WAVE, HEART, STAR)
        17 -> intArrayOf(LEAF, HEART, STAR, BUBBLE)
        else -> intArrayOf(PHONE, BUBBLE, HEART)
    }
    val count = when (index) {
        5, 6, 8 -> 58
        13, 12 -> 40
        else -> 34
    }
    val random = Random(index * 7919 + season.key.hashCode())
    return List(count) {
        Doodle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            s = 0.011f + random.nextFloat() * 0.030f,
            rot = (random.nextFloat() - 0.5f) * 36f,
            kind = kinds[random.nextInt(kinds.size)],
        )
    }
}

private fun DrawScope.drawDoodle(kind: Int, x: Float, y: Float, s: Float, color: Color) {
    val line = (s * 0.045f).coerceAtLeast(1.2f)
    val stroke = Stroke(width = line)
    when (kind) {
        PHONE -> {
            val w = s * 0.42f
            val h = s * 0.78f
            drawRoundRect(color, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(s * 0.10f), style = stroke)
            drawCircle(color, s * 0.045f, Offset(x, y + h * 0.32f), style = stroke)
            drawLine(color, Offset(x - w * 0.22f, y - h * 0.30f), Offset(x + w * 0.22f, y - h * 0.30f), line)
        }
        BUBBLE -> {
            drawRoundRect(color, Offset(x - s * 0.48f, y - s * 0.34f), Size(s * 0.96f, s * 0.58f), CornerRadius(s * 0.20f), style = stroke)
            val tail = Path().apply {
                moveTo(x + s * 0.02f, y + s * 0.22f)
                lineTo(x + s * 0.16f, y + s * 0.50f)
                lineTo(x + s * 0.30f, y + s * 0.22f)
            }
            drawPath(tail, color, style = stroke)
        }
        PLANE -> {
            val p = Path().apply {
                moveTo(x - s * 0.48f, y - s * 0.34f)
                lineTo(x + s * 0.50f, y)
                lineTo(x - s * 0.48f, y + s * 0.34f)
                lineTo(x - s * 0.14f, y)
                close()
            }
            drawPath(p, color, style = stroke)
            drawLine(color, Offset(x - s * 0.14f, y), Offset(x + s * 0.16f, y), line)
        }
        HEART -> {
            val p = Path().apply {
                moveTo(x, y + s * 0.34f)
                cubicTo(x - s * 0.55f, y - s * 0.02f, x - s * 0.38f, y - s * 0.48f, x, y - s * 0.16f)
                cubicTo(x + s * 0.38f, y - s * 0.48f, x + s * 0.55f, y - s * 0.02f, x, y + s * 0.34f)
                close()
            }
            drawPath(p, color, style = stroke)
        }
        STAR, SPARK -> {
            val points = if (kind == SPARK) 4 else 5
            val p = Path()
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 0) s * 0.46f else s * 0.18f
                val a = -Math.PI / 2 + i * Math.PI / points
                val px = x + cos(a).toFloat() * r
                val py = y + sin(a).toFloat() * r
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            drawPath(p, color, style = stroke)
        }
        SNOW -> {
            for (i in 0 until 3) {
                val a = i * Math.PI / 3
                val dx = cos(a).toFloat() * s * 0.52f
                val dy = sin(a).toFloat() * s * 0.52f
                drawLine(color, Offset(x - dx, y - dy), Offset(x + dx, y + dy), line)
            }
            drawCircle(color, s * 0.06f, Offset(x, y), style = stroke)
        }
        LEAF -> {
            drawOval(color, Offset(x - s * 0.20f, y - s * 0.46f), Size(s * 0.40f, s * 0.92f), style = stroke)
            drawLine(color, Offset(x, y - s * 0.36f), Offset(x, y + s * 0.42f), line)
        }
        WAVE -> {
            for (i in -1..1) {
                val yy = y + i * s * 0.25f
                val p = Path().apply {
                    moveTo(x - s * 0.55f, yy)
                    cubicTo(x - s * 0.20f, yy - s * 0.22f, x + s * 0.20f, yy + s * 0.22f, x + s * 0.55f, yy)
                }
                drawPath(p, color, style = stroke)
            }
        }
        BUILDING -> {
            drawRect(color, Offset(x - s * 0.42f, y - s * 0.18f), Size(s * 0.36f, s * 0.70f), style = stroke)
            drawRect(color, Offset(x + s * 0.02f, y - s * 0.42f), Size(s * 0.38f, s * 0.94f), style = stroke)
            for (i in 0 until 3) drawLine(color, Offset(x + s * 0.12f, y - s * 0.22f + i * s * 0.18f), Offset(x + s * 0.30f, y - s * 0.22f + i * s * 0.18f), line)
        }
        PLANET -> {
            drawCircle(color, s * 0.26f, Offset(x, y), style = stroke)
            drawOval(color, Offset(x - s * 0.52f, y - s * 0.14f), Size(s * 1.04f, s * 0.28f), style = stroke)
        }
        TREE -> {
            val p = Path().apply {
                moveTo(x, y - s * 0.52f)
                lineTo(x - s * 0.34f, y + s * 0.05f)
                lineTo(x + s * 0.34f, y + s * 0.05f)
                close()
                moveTo(x, y - s * 0.22f)
                lineTo(x - s * 0.42f, y + s * 0.34f)
                lineTo(x + s * 0.42f, y + s * 0.34f)
                close()
            }
            drawPath(p, color, style = stroke)
            drawLine(color, Offset(x, y + s * 0.30f), Offset(x, y + s * 0.52f), line)
        }
    }
}
