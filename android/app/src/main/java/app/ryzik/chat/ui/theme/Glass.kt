package app.ryzik.chat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Включён ли Liquid Glass (см. «Оформление»). Значение даёт [RyzikTheme]. */
val LocalLiquidGlass = compositionLocalOf { false }

/**
 * Настоящее «жидкое стекло»: полупрозрачная толща, блик сверху,
 * глянцевая кромка по контуру и внутренняя тень у нижнего края.
 *
 * Если эффект выключен, панель просто заливается [color] — как обычная карточка.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 0.dp,
): Modifier {
    if (!LocalLiquidGlass.current) {
        val base = if (elevation > 0.dp) shadow(elevation, shape, clip = false) else Modifier
        return base.clip(shape).background(color)
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Тень под стеклом не рисуем: она просвечивает сквозь полупрозрачную панель
    // и выглядит грязно, а на старых Android ещё и стоит денег по fps.
    return clip(shape).glassPaint(shape, color, dark)
}

private fun Modifier.glassPaint(shape: Shape, color: Color, dark: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    // Толща стекла: сверху чуть плотнее, снизу прозрачнее.
    val fill = Brush.verticalGradient(
        colors = listOf(
            color.copy(alpha = if (dark) 0.84f else 0.88f),
            color.copy(alpha = if (dark) 0.66f else 0.72f),
        ),
        startY = 0f,
        endY = h,
    )
    // Верхний блик — «мокрый» отблеск.
    val sheen = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.24f), Color.Transparent),
        startY = 0f,
        endY = (h * 0.55f).coerceAtLeast(1f),
    )
    // Глянцевая кромка: яркая сверху-слева, гаснет к низу-справа и снова ловит свет.
    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (dark) 0.44f else 0.92f),
            Color.White.copy(alpha = if (dark) 0.14f else 0.34f),
            Color.White.copy(alpha = if (dark) 0.05f else 0.10f),
            Color.White.copy(alpha = if (dark) 0.22f else 0.48f),
        ),
        start = Offset(0f, 0f),
        end = Offset(w, h),
    )
    // Внутренняя тень: ощущение толщины материала.
    val shade = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.30f else 0.12f)),
        start = Offset(0f, 0f),
        end = Offset(w, h),
    )
    val rimPx = 2.4.dp.toPx()
    val shadePx = 4.dp.toPx()
    onDrawWithContent {
        drawOutline(outline, fill)
        drawOutline(outline, sheen)
        drawContent()
        drawOutline(outline, shade, style = Stroke(shadePx))
        drawOutline(outline, rim, style = Stroke(rimPx))
    }
}
