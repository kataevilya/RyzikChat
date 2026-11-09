package app.ryzik.chat.ui.components

import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.data.Badge
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val avatarPalette = listOf(
    Color(0xFFFF8A3D), Color(0xFF7C4DFF), Color(0xFF00A3A3), Color(0xFF2E6BE6),
    Color(0xFF43A047), Color(0xFFE91E63), Color(0xFFFFB300), Color(0xFF8D6E63),
)

/** Форма аватарок в списках: скруглённый квадрат. */
val AvatarShape = RoundedCornerShape(percent = 32)

/** Аватар: картинка, а если её нет — цветной квадрат со скруглением и первыми буквами имени. */
@Composable
fun Avatar(
    name: String,
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    saved: Boolean = false,
    service: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = AvatarShape,
) {
    Box(modifier.size(size)) {
        val base = if (service) MaterialTheme.colorScheme.primary else avatarPalette[Math.floorMod(name.hashCode(), avatarPalette.size)]
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            when {
                saved -> Icon(Icons.Rounded.Bookmark, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
                service -> Icon(Icons.Rounded.Verified, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
                else -> Text(
                    initials(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
            if (url != null && !saved && !service) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
            }
        }
        AnimatedVisibility(
            visible = online,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)),
            exit = scaleOut(),
        ) {
            Box(
                Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759))
            )
        }
    }
}

/** Большой значок в круге для пустых экранов (вместо эмодзи). */
@Composable
fun EmptyIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(88.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp)) }
}

fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6750A4))

/** Маленькие значки рядом с именем. Выдаёт их только администратор. */
@Composable
fun BadgeIcons(badges: List<Badge>, isAdmin: Boolean = false, size: Dp = 18.dp, isPremium: Boolean = false, emojiStatus: String? = null, verified: Boolean = false) {
    if (badges.isEmpty() && !isAdmin && !isPremium && !verified) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(4.dp))
        if (verified) VerifiedMark(size)
        if (isPremium) {
            // С Премиумом вместо звезды можно поставить свой эмодзи-статус.
            if (!emojiStatus.isNullOrBlank()) Text(emojiStatus, fontSize = (size.value * 0.9f).sp) else PremiumStar(size)
        }
        if (isAdmin) Icon(Icons.Rounded.Shield, "Администратор", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size))
        badges.take(3).forEach { b ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(parseColor(b.color).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Text(b.emoji, fontSize = (size.value * 0.62f).sp) }
        }
        if (badges.size > 3) Text("+${badges.size - 3}", style = MaterialTheme.typography.labelSmall)
    }
}

/** Большая плашка бейджа для профиля, с лёгким «переливом». */
@Composable
fun BadgeChip(badge: Badge, modifier: Modifier = Modifier) {
    val color = parseColor(badge.color)
    val shimmer = rememberInfiniteTransition(label = "badge")
    val shift by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "shift")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.18f + 0.12f * shift), color.copy(alpha = 0.30f - 0.12f * shift))))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badge.id == "admin") {
            Icon(Icons.Rounded.Verified, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        } else if (badge.emoji.isNotBlank()) {
            Text(badge.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(badge.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun NameWithBadges(name: String, badges: List<Badge>, isAdmin: Boolean, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, isPremium: Boolean = false, emojiStatus: String? = null, verified: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        BadgeIcons(badges, isAdmin, size = (style.fontSize.value + 2).dp, isPremium = isPremium, emojiStatus = emojiStatus, verified = verified)
    }
}

/** Голубая галочка верификации. */
val VerifiedBlue = Color(0xFF2F9BF5)

@Composable
fun VerifiedMark(size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.Verified, "Верифицирован", tint = VerifiedBlue, modifier = modifier.size(size))
}

/** Три прыгающие точки «печатает…». */
@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.primary, dot: Dp = 5.dp) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(500, delayMillis = i * 120, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer { translationY = -y * dot.toPx() * 0.8f }
                    .scale(0.8f + 0.2f * y)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.5f + 0.5f * y))
            )
        }
    }
}

// ---------- Даты ----------

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(a: Long, b: Long) = sameDay(a, b)

fun formatTime(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

fun formatListTime(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> formatTime(t)
        now - t < 6 * 24 * 3600_000L -> SimpleDateFormat("EE", Locale("ru")).format(Date(t))
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(t))
    }
}

fun formatDay(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> "Сегодня"
        sameDay(t, now - 24 * 3600_000L) -> "Вчера"
        else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(t))
    }
}

fun formatLastSeen(online: Boolean, lastSeen: Long): String {
    if (online) return "в сети"
    if (lastSeen <= 0) return "был(а) давно"
    val diff = System.currentTimeMillis() - lastSeen
    return when {
        diff < 60_000 -> "был(а) только что"
        diff < 3600_000 -> "был(а) ${diff / 60_000} мин. назад"
        sameDay(lastSeen, System.currentTimeMillis()) -> "был(а) в ${formatTime(lastSeen)}"
        else -> "был(а) ${formatDay(lastSeen).lowercase()} в ${formatTime(lastSeen)}"
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "%.1f КБ".format(bytes / 1024f)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / 1024f / 1024f)
    else -> "%.2f ГБ".format(bytes / 1024f / 1024f / 1024f)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

val PremiumGradient = listOf(Color(0xFF8E5CFF), Color(0xFFFF5CA8), Color(0xFFFFB443))

/** Переливающаяся звезда Премиума рядом с именем. */
@Composable
fun PremiumStar(size: Dp = 18.dp) {
    val t = rememberInfiniteTransition(label = "star")
    val turn by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "turn")
    Icon(
        Icons.Rounded.Star,
        "Премиум",
        modifier = Modifier
            .size(size)
            .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
            .drawWithCache {
                val brush = Brush.linearGradient(PremiumGradient, start = Offset(0f, this.size.height * turn), end = Offset(this.size.width, this.size.height * (1 - turn)))
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcAtop)
                }
            },
        tint = Color.White,
    )
}
