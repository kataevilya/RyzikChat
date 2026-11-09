package app.ryzik.chat.ui.flux

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.GiftItem
import app.ryzik.chat.data.GiftRef
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Анимации подарков. Ключи совпадают с сервером. */
val GiftAnimations = listOf(
    "none" to "Без анимации",
    "bounce" to "Прыжки",
    "pulse" to "Пульс",
    "sway" to "Качание",
    "shake" to "Тряска",
    "spin" to "Вращение",
    "float" to "Парение",
    "shine" to "Сияние",
)

@Composable
fun GiftImage(item: GiftItem, modifier: Modifier = Modifier) =
    GiftImage(item.fileId, modifier, item.emoji, item.animation, item.caption)

@Composable
fun GiftImage(ref: GiftRef, modifier: Modifier = Modifier) =
    GiftImage(ref.fileId, modifier, ref.emoji, ref.animation, ref.caption)

/**
 * Подарок: эмодзи или картинка в скруглённой рамке с мягким градиентом,
 * с анимацией и надписью (например, «Доброе утро») поверх.
 */
@Composable
fun GiftImage(
    fileId: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    animation: String = "none",
    caption: String = "",
) {
    val t = rememberInfiniteTransition(label = "gift")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave")
    val loop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "loop")
    val sparkle = animation == "shine" || animation == "spin" || animation == "pulse"

    BoxWithConstraints(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(FluxGradient.map { it.copy(alpha = 0.18f) })),
        contentAlignment = Alignment.Center,
    ) {
        val side = maxWidth
        // Блик, пробегающий по подарку
        if (animation == "shine") {
            Canvas(Modifier.fillMaxSize()) {
                val x = -size.width + loop * size.width * 3f
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + size.width * 0.6f, size.height),
                    ),
                )
            }
        }
        // Искорки по кругу
        if (sparkle) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.42f
                for (i in 0 until 8) {
                    val a = (i / 8.0 + loop) * 2 * PI
                    val tw = ((sin(a * 3) + 1) / 2).toFloat()
                    drawCircle(
                        FluxGradient[i % FluxGradient.size].copy(alpha = 0.35f + 0.5f * tw),
                        radius = size.minDimension * (0.012f + 0.018f * tw),
                        center = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().padding(side * 0.1f).graphicsLayer {
                when (animation) {
                    "bounce" -> { translationY = -wave * size.height * 0.08f; scaleY = 0.96f + 0.04f * wave; scaleX = 1.02f - 0.02f * wave }
                    "pulse" -> { scaleX = 0.9f + 0.12f * wave; scaleY = 0.9f + 0.12f * wave }
                    "sway" -> { rotationZ = -10f + 20f * wave; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                    "shake" -> { rotationZ = sin(loop * 2 * PI * 6).toFloat() * 6f * (if (loop < 0.35f) 1f else 0f) }
                    "spin" -> { rotationY = loop * 360f; cameraDistance = 12f * density }
                    "float" -> { translationY = -wave * size.height * 0.1f; rotationZ = -4f + 8f * wave }
                    "shine" -> { scaleX = 0.97f + 0.05f * wave; scaleY = 0.97f + 0.05f * wave }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (!emoji.isNullOrBlank()) {
                Text(emoji, fontSize = (side.value * 0.55f).sp, lineHeight = (side.value * 0.62f).sp, textAlign = TextAlign.Center)
            } else if (fileId.isNotBlank()) {
                AsyncImage(RyzikApp.instance.repo.avatarUrl(fileId), null, Modifier.fillMaxSize())
            }
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (side.value * 0.09f).coerceIn(9f, 18f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = side * 0.06f)
                    .graphicsLayer { scaleX = 0.96f + 0.06f * wave; scaleY = 0.96f + 0.06f * wave }
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(FluxGradient))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
