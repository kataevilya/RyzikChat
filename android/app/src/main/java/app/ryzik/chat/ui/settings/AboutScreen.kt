package app.ryzik.chat.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.BuildConfig
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.R
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.ui.components.formatSize
import app.ryzik.chat.ui.flux.Confetti
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AboutPurple = Color(0xFF7C4DFF)
private val AboutDeep = Color(0xFF2E0B72)

/**
 * Экран «О приложении». Семь нажатий на версию открывают режим разработчика —
 * после этого здесь появляется раздел с диагностикой.
 */
@Composable
fun AboutContent(onOpenTerms: () -> Unit, onOpenDev: () -> Unit) {
    val app = RyzikApp.instance
    val repo = app.repo
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val connected by repo.connected.collectAsState()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me

    var taps by remember { mutableIntStateOf(0) }
    var cache by remember { mutableLongStateOf(-1L) }
    val devMode = settings.devMode

    LaunchedEffect(Unit) { cache = withContext(Dispatchers.IO) { repo.cacheBytes() } }
    LaunchedEffect(taps) {
        if (taps in 1..6) {
            delay(2600)
            taps = 0
        }
    }

    val onVersionTap: () -> Unit = {
        if (devMode) {
            onOpenDev()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            taps += 1
            if (taps >= 7) {
                taps = 0
                Confetti.fire()
                scope.launch {
                    app.prefs.update { it.copy(devMode = true) }
                    Toast.makeText(context, "Режим разработчика включён", Toast.LENGTH_SHORT).show()
                    onOpenDev()
                }
            }
        }
    }

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        AboutHero(
            devMode = devMode,
            taps = taps,
            onVersionTap = onVersionTap,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                title = "Связь",
                value = if (connected) "онлайн" else "офлайн",
                tint = if (connected) Color(0xFF2FBF71) else Color(0xFFFF5A5F),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Кэш",
                value = if (cache < 0) "…" else formatSize(cache),
                tint = AboutPurple,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Ключ",
                value = repo.myFingerprint().split(" ").take(2).joinToString(" ").ifBlank { "—" },
                tint = Color(0xFF3D8BFF),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("О мессенджере")
        CardGroup {
            AboutRow(Icons.Rounded.Security, Color(0xFF2FBF71), "Сквозное шифрование", "X25519 и AES-256-GCM, ключи только у вас")
            Divider()
            AboutRow(Icons.Rounded.Storage, Color(0xFF00B3A4), "Свой сервер", if (me == null) "RyzikChat" else "Вы подключены к своему серверу")
            Divider()
            AboutRow(Icons.Rounded.Favorite, Color(0xFFFF4F8B), "Без рекламы и слежки", "Проект живёт на поддержке пользователей")
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Проект")
        CardGroup {
            AboutRow(Icons.Rounded.Description, Color(0xFF8A8FA3), "Правила использования", "Что можно и что нельзя", onOpenTerms)
            Divider()
            AboutRow(Icons.Rounded.Code, Color(0xFF5C6BC0), "Исходный код", "github.com/kataevilya/RyzikChat") { open("https://github.com/kataevilya/RyzikChat") }
            Divider()
            AboutRow(Icons.AutoMirrored.Rounded.Send, Color(0xFF2AABEE), "Написать владельцу", "@ryzik3489 в Telegram") { open("https://t.me/ryzik3489") }
            Divider()
            AboutRow(Icons.Rounded.Language, Color(0xFF00A8E8), "Адрес сервера", repo.api.baseUrl) { open(repo.api.baseUrl) }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Технологии")
        TechChips(listOf("Kotlin", "Jetpack Compose", "X25519", "AES-256-GCM", "SQLite", "WebSocket", "Material You", "Node.js"))

        if (devMode) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Разработка")
            DevEntryCard(onOpenDev)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "RyzikChat · версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Сделано вручную, с любовью к деталям",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutHero(devMode: Boolean, taps: Int, onVersionTap: () -> Unit) {
    val shine = rememberInfiniteTransition(label = "hero")
    val pos by shine.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "shine",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(224.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(AboutPurple, Color(0xFF5A1FD0), AboutDeep),
                    start = Offset.Zero,
                    end = Offset(900f, 900f),
                ),
            ),
    ) {
        // Мягкие блики в углах.
        Box(
            Modifier
                .size(190.dp)
                .offset(x = 180.dp, y = (-70).dp)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent))),
        )
        Box(
            Modifier
                .size(150.dp)
                .offset(x = (-50).dp, y = 140.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFFF5CA8).copy(alpha = 0.30f), Color.Transparent))),
        )
        // Бегущий блик «стекла».
        Canvas(Modifier.fillMaxSize()) {
            val band = size.width * 0.38f
            val x = -band + (size.width + band) * pos
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + band, size.height),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("RyzikChat", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            Text(
                "Сквозное шифрование · свой сервер",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onVersionTap)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (devMode) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFB300)).padding(horizontal = 7.dp, vertical = 1.dp),
                        ) {
                            Text("DEV", color = Color(0xFF2E0B72), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (taps in 1..6) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(7) { i ->
                        Box(
                            Modifier
                                .size(if (i < taps) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i < taps) 0.95f else 0.35f)),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("ещё ${7 - taps}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun CardGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun AboutRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechChips(items: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    item,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DevEntryCard(onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, AboutPurple.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = AboutPurple.copy(alpha = 0.10f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(AboutPurple),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Key, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Для разработчиков", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Диагностика, ключи, кэш, журнал и отладка интерфейса",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = AboutPurple,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
