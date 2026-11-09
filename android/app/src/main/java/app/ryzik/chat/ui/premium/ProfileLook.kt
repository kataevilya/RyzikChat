package app.ryzik.chat.ui.premium

import androidx.compose.material.icons.rounded.Shield
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.ui.components.VerifiedMark
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.ProfileStyle
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.PremiumGradient
import app.ryzik.chat.ui.components.PremiumStar
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ================= Наборы для оформления =================

object ProfileLook {
    /** Готовые градиенты шапки. */
    val gradients = listOf(
        "#8E5CFF" to "#FF5CA8", "#FF5CA8" to "#FFB443", "#00C6FF" to "#0072FF", "#11998E" to "#38EF7D",
        "#FC466B" to "#3F5EFB", "#F7971E" to "#FFD200", "#654EA3" to "#EAAFC8", "#FF416C" to "#FF4B2B",
        "#1D2B64" to "#F8CDDA", "#00B09B" to "#96C93D", "#2C3E50" to "#4CA1AF", "#DA22FF" to "#9733EE",
        "#FF9A9E" to "#FAD0C4", "#0F2027" to "#2C5364", "#B24592" to "#F15F79", "#43C6AC" to "#191654",
    )
    val nameColors = listOf(
        "#FF5CA8", "#8E5CFF", "#FFB443", "#00C6FF", "#38EF7D", "#FF4B2B", "#FFD200", "#9733EE", "#4CA1AF", "#F15F79",
    )
    val patterns = listOf("⭐", "❤️", "🔥", "✨", "🌸", "🍄", "💎", "🎮", "🌙", "⚡", "🎵", "🍀", "🐱", "👑", "🌈", "❄️", "🦋", "🍓", "🚀", "💜")
    val statuses = listOf(
        "😎", "🔥", "❤️", "✨", "⭐", "👑", "💎", "🚀", "🎮", "🎧", "🎵", "📚", "💻", "☕", "🍕", "🍄",
        "🌸", "🌙", "☀️", "⚡", "🌈", "❄️", "🦋", "🐱", "🐶", "🦊", "🐼", "🦄", "🍀", "🎉", "🏆", "⚽",
        "💪", "🤝", "🙏", "😴", "🤔", "😇", "🥳", "😈", "👻", "💀", "🤖", "👾", "🫶", "💜", "🧡", "💚",
    )
    val effects = listOf("none" to "Без эффекта", "float" to "Парение", "sparkle" to "Мерцание", "shimmer" to "Блик")
    val rings = listOf("none" to "Обычная", "gradient" to "Радуга", "glow" to "Сияние", "pulse" to "Пульс")
    val fonts = listOf("default" to "Обычный", "serif" to "С засечками", "mono" to "Моно", "cursive" to "Рукописный")
}

fun hexColor(hex: String?): Color? = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

fun fontOf(key: String?): FontFamily? = when (key) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> null
}

/** Эмодзи-статус рядом с именем: слегка покачивается. */
@Composable
fun EmojiStatus(emoji: String, size: TextUnit = 20.sp) {
    val t = rememberInfiniteTransition(label = "status")
    val tilt by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    Text(emoji, fontSize = size, modifier = Modifier.padding(start = 4.dp).graphicsLayer { rotationZ = tilt })
}

// Места для эмодзи узора: x, y (доли ширины и высоты), размер, поворот.
private val patternSpots = listOf(
    floatArrayOf(0.06f, 0.10f, 22f, -15f), floatArrayOf(0.22f, 0.30f, 16f, 10f), floatArrayOf(0.40f, 0.06f, 20f, 20f),
    floatArrayOf(0.60f, 0.24f, 14f, -8f), floatArrayOf(0.78f, 0.08f, 24f, 12f), floatArrayOf(0.92f, 0.34f, 18f, -20f),
    floatArrayOf(0.10f, 0.55f, 18f, 25f), floatArrayOf(0.30f, 0.66f, 22f, -10f), floatArrayOf(0.70f, 0.60f, 22f, 8f),
    floatArrayOf(0.88f, 0.70f, 16f, -25f), floatArrayOf(0.50f, 0.45f, 12f, 0f), floatArrayOf(0.16f, 0.86f, 14f, 15f),
    floatArrayOf(0.84f, 0.92f, 14f, -12f), floatArrayOf(0.02f, 0.80f, 12f, 30f), floatArrayOf(0.97f, 0.52f, 12f, 5f),
)

/**
 * Шапка профиля. С Премиумом: свой градиент или обложка, узор из эмодзи с эффектом,
 * рамка вокруг аватарки, цвет и шрифт имени, эмодзи-статус.
 */
@Composable
fun ProfileHeader(
    name: String,
    avatarUrl: String?,
    online: Boolean,
    isPremium: Boolean,
    isAdmin: Boolean,
    emojiStatus: String?,
    style: ProfileStyle?,
    bannerUrl: String?,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    avatarSize: Dp = 112.dp,
    avatarScale: Float = 1f,
) {
    val s = if (isPremium) style else null
    val c1 = hexColor(s?.color1) ?: MaterialTheme.colorScheme.primaryContainer
    val c2 = hexColor(s?.color2) ?: MaterialTheme.colorScheme.tertiaryContainer
    val bannerHeight = 170.dp
    val t = rememberInfiniteTransition(label = "header")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "phase")

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(bannerHeight + avatarSize / 2)) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(c1, c2)))
                    .drawWithContent {
                        drawContent()
                        if (s?.effect == "shimmer") {
                            val w = size.width
                            val x = -w + phase * w * 3
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                    start = Offset(x, 0f), end = Offset(x + w * 0.5f, size.height),
                                )
                            )
                        }
                    },
            ) {
                if (bannerUrl != null && s != null) {
                    AsyncImage(bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                }
                val pattern = s?.pattern
                if (!pattern.isNullOrBlank()) {
                    val w = maxWidth
                    val h = maxHeight
                    patternSpots.forEachIndexed { i, spot ->
                        val wave = sin((phase + i * 0.13f) * 2 * PI).toFloat()
                        val dy = if (s?.effect == "float") wave * 6f else 0f
                        val alpha = if (s?.effect == "sparkle") 0.25f + 0.6f * abs(wave) else 0.55f
                        Text(
                            pattern,
                            fontSize = spot[2].sp,
                            modifier = Modifier
                                .offset(x = w * spot[0] - (spot[2] / 2).dp, y = h * spot[1] - (spot[2] / 2).dp + dy.dp)
                                .graphicsLayer { rotationZ = spot[3]; this.alpha = alpha },
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { scaleX = avatarScale; scaleY = avatarScale }) {
                AvatarWithRing(name, avatarUrl, avatarSize, online, s?.ring, c1, c2, phase)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (isPremium) FontWeight.Bold else null,
                fontFamily = fontOf(s?.font),
                color = hexColor(s?.nameColor) ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (verified) { Spacer(Modifier.width(4.dp)); VerifiedMark(24.dp) }
            if (isPremium) {
                if (!emojiStatus.isNullOrBlank()) EmojiStatus(emojiStatus, 24.sp) else { Spacer(Modifier.width(4.dp)); PremiumStar(24.dp) }
            }
            if (isAdmin) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Shield, "Администратор", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun AvatarWithRing(name: String, url: String?, size: Dp, online: Boolean, ring: String?, c1: Color, c2: Color, phase: Float) {
    val surface = MaterialTheme.colorScheme.surface
    val ringWidth = 5.dp
    Box(
        Modifier
            .size(size + ringWidth * 2)
            .drawBehind {
                val r = this.size.minDimension / 2
                when (ring) {
                    "gradient" -> rotate(phase * 360f) {
                        drawCircle(Brush.sweepGradient(listOf(c1, c2, Color(0xFFFFB443), c1)), radius = r)
                    }
                    "glow" -> {
                        val k = 0.5f + 0.5f * sin(phase * 2 * PI).toFloat()
                        drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.9f), c2.copy(alpha = 0f)), radius = r * (1.25f + 0.1f * k)), radius = r * (1.25f + 0.1f * k))
                        drawCircle(Brush.linearGradient(listOf(c1, c2)), radius = r)
                    }
                    "pulse" -> {
                        drawCircle(c1, radius = r)
                        drawCircle(c2.copy(alpha = 1f - phase), radius = r * (1f + 0.35f * phase), style = Stroke(width = 3.dp.toPx()))
                    }
                    else -> drawCircle(surface, radius = r)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).border(BorderStroke(3.dp, surface), CircleShape)) {
            Avatar(name, url, size, online = online, shape = CircleShape)
        }
    }
}

// ================= Экран настройки =================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileLookScreen(onBack: () -> Unit, onOpenPremium: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    val premium = me.isPremium
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(me.profileStyle ?: ProfileStyle()) }
    var status by remember { mutableStateOf(me.emojiStatus.orEmpty()) }
    var banner by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) banner = uri }

    fun save() {
        if (!premium) { onOpenPremium(); return }
        saving = true
        scope.launch {
            runCatching {
                var st = style
                banner?.let { st = st.copy(bannerFileId = repo.uploadAvatar(it)) }
                repo.updatePremiumLook(status, st.takeIf { it != ProfileStyle() })
            }.onSuccess { onBack() }.onFailure { error = it.userMessage() }
            saving = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Оформление профиля") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            actions = {
                if (saving) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                else TextButton(onClick = ::save) { Text(if (premium) "Сохранить" else "Получить Премиум") }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ProfileHeader(
                    name = me.displayName,
                    avatarUrl = repo.avatarUrl(me.avatarFileId),
                    online = false,
                    isPremium = true,
                    isAdmin = me.isAdmin,
                    emojiStatus = status,
                    style = style,
                    bannerUrl = banner?.toString() ?: repo.avatarUrl(style.bannerFileId),
                )
                Text(
                    "@${me.username}",
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!premium) item {
                ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(PremiumGradient)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Star, null, tint = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Доступно с Премиумом", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Попробуйте, как будет выглядеть профиль. Сохранить можно с Премиумом.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }

            item { Section("Эмодзи-статус", "Виден рядом с вашим именем везде в приложении") }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Choice(selected = status.isBlank(), onClick = { status = "" }) {
                        Icon(Icons.Rounded.Block, "Без статуса", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ProfileLook.statuses.forEach { e ->
                        Choice(selected = status == e, onClick = { status = e }) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            item { Section("Цвет шапки", null) }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Choice(selected = style.color1 == null, onClick = { style = style.copy(color1 = null, color2 = null) }, size = 48.dp) {
                            Icon(Icons.Rounded.Block, "Стандартный", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(ProfileLook.gradients) { (a, b) ->
                        Choice(
                            selected = style.color1 == a && style.color2 == b,
                            onClick = { style = style.copy(color1 = a, color2 = b) },
                            size = 48.dp,
                            background = Brush.linearGradient(listOf(hexColor(a)!!, hexColor(b)!!)),
                        ) {}
                    }
                }
            }

            item { Section("Обложка", "Своя картинка в шапке профиля") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать фото")
                    }
                    if (banner != null || style.bannerFileId != null) {
                        TextButton(onClick = { banner = null; style = style.copy(bannerFileId = null) }) { Text("Убрать") }
                    }
                }
            }

            item { Section("Узор", "Эмодзи, рассыпанные по шапке") }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Choice(selected = style.pattern == null, onClick = { style = style.copy(pattern = null) }) {
                            Icon(Icons.Rounded.Block, "Без узора", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(ProfileLook.patterns) { e ->
                        Choice(selected = style.pattern == e, onClick = { style = style.copy(pattern = e) }) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            item { Section("Эффект шапки", null) }
            item {
                ChipRow(ProfileLook.effects, style.effect ?: "none") { v -> style = style.copy(effect = v.takeIf { it != "none" }) }
            }

            item { Section("Рамка аватарки", null) }
            item {
                ChipRow(ProfileLook.rings, style.ring ?: "none") { v -> style = style.copy(ring = v.takeIf { it != "none" }) }
            }

            item { Section("Цвет имени", null) }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Choice(selected = style.nameColor == null, onClick = { style = style.copy(nameColor = null) }, size = 40.dp) {
                            Icon(Icons.Rounded.Block, "Обычный", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(ProfileLook.nameColors) { c ->
                        Choice(
                            selected = style.nameColor == c,
                            onClick = { style = style.copy(nameColor = c) },
                            size = 40.dp,
                            background = androidx.compose.ui.graphics.SolidColor(hexColor(c)!!),
                        ) {}
                    }
                }
            }

            item { Section("Шрифт имени", null) }
            item {
                ChipRow(ProfileLook.fonts, style.font ?: "default") { v -> style = style.copy(font = v.takeIf { it != "default" }) }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { style = ProfileStyle(); status = ""; banner = null }, modifier = Modifier.weight(1f)) { Text("Сбросить") }
                    Button(onClick = ::save, enabled = !saving, modifier = Modifier.weight(1f)) {
                        Text(if (premium) "Сохранить" else "Получить Премиум")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    background: Brush? = null,
    content: @Composable () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(background ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (selected && background != null) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.4f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label, fontFamily = if (options === ProfileLook.fonts) fontOf(key) else null) },
            )
        }
    }
}
