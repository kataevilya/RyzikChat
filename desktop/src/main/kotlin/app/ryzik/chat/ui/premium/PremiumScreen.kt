package app.ryzik.chat.ui.premium

import androidx.compose.material.icons.rounded.Bolt
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.PremiumGradient
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

const val PREMIUM_CONTACT = "ryzik3489"

/** Экран Премиума. Получить его можно, написав владельцу в Telegram. */
@Composable
fun PremiumScreen(onBack: () -> Unit, onOpenFlux: () -> Unit = {}) {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf(1000L) }
    var buying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var bought by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { repo.refreshMe() }
        runCatching { repo.api.flux() }.onSuccess { price = it.premiumMonthPrice }
    }

    val t = rememberInfiniteTransition(label = "premium")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0F33), Color(0xFF2B1452), Color(0xFF120A24)))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Color.White) }
            }
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                // Вращающиеся искры вокруг звезды
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
                    val r = size.minDimension / 2.3f
                    for (i in 0 until 12) {
                        val a = Math.toRadians(i * 30.0)
                        val c = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
                        drawCircle(PremiumGradient[i % PremiumGradient.size].copy(alpha = 0.8f), radius = if (i % 2 == 0) 5f else 3f, center = c)
                    }
                }
                Box(
                    Modifier
                        .size(110.dp)
                        .graphicsLayer { scaleX = glow; scaleY = glow }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(PremiumGradient)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
            }
            Text("RyzikChat Премиум", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (me?.isPremium == true && me.premiumUntil != null) "Премиум активен до ${app.ryzik.chat.ui.flux.dateText(me.premiumUntil)}"
                else if (me?.isPremium == true) "У вас уже есть Премиум. Спасибо за поддержку!"
                else "Больше возможностей и звезда рядом с именем",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))

            val perks = listOf(
                Perk(Icons.Rounded.Star, "Звезда или эмодзи-статус", "Переливающаяся звезда у имени или любой эмодзи на ваш выбор"),
                Perk(Icons.Rounded.Palette, "Оформление профиля", "Цвет и обложка шапки, узор из эмодзи с эффектами, рамка аватарки, цвет и шрифт имени"),
                Perk(Icons.Rounded.CloudUpload, "Файлы до 2 ГБ", "Вместо 200 МБ без Премиума"),
                Perk(Icons.Rounded.CropSquare, "Длинные квадратики", "Видеосообщения до 2 минут вместо 1"),
                Perk(Icons.Rounded.Mic, "Длинные голосовые", "До 30 минут вместо 10"),
                Perk(Icons.Rounded.Palette, "Особые цвета", "Эксклюзивные цвета оформления приложения"),
                Perk(Icons.Rounded.Star, "Иконка приложения", "Другой цвет иконки RyzikChat на главном экране телефона"),
                Perk(Icons.Rounded.Verified, "Поддержка проекта", "Вы помогаете RyzikChat развиваться"),
            )
            perks.forEachIndexed { i, p -> PerkRow(p, i) }

            if (AppIcons.supported) {
                Spacer(Modifier.height(16.dp))
                AppIconPicker(me?.isPremium == true) { error = it }
            }

            Spacer(Modifier.height(24.dp))
            if (me != null && (!me.isPremium || me.premiumUntil != null)) {
                val balance = me.flux
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (me.isPremium) "Продлить за FLUX" else "Купить за FLUX", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Месяц Премиума: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            app.ryzik.chat.ui.flux.FluxAmount(price)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("У вас: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            app.ryzik.chat.ui.flux.FluxAmount(balance, bold = false)
                        }
                        if (bought) {
                            Spacer(Modifier.height(8.dp))
                            Text("Готово! Премиум активен до ${me.premiumUntil?.let { app.ryzik.chat.ui.flux.dateText(it) } ?: ""}", color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        }
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                        if (balance >= price) {
                            Button(
                                onClick = {
                                    buying = true; error = null
                                    scope.launch {
                                        runCatching { repo.buyPremium(1) }
                                            .onSuccess { bought = true }
                                            .onFailure { error = it.userMessage() }
                                        buying = false
                                    }
                                },
                                enabled = !buying,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Icon(Icons.Rounded.Bolt, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (buying) "Покупаем..." else "Купить месяц за ${app.ryzik.chat.ui.flux.formatFlux(price)} FLUX")
                            }
                        } else {
                            Text("Не хватает ${app.ryzik.chat.ui.flux.formatFlux(price - balance)} FLUX", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onOpenFlux, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Rounded.Bolt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Открыть FLUX")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (me?.isPremium != true) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Другой способ", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Чтобы получить Премиум, напишите владельцу @$PREMIUM_CONTACT в Telegram. Укажите свой ник в RyzikChat: @${me?.username.orEmpty()}",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$PREMIUM_CONTACT")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(i) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Написать @$PREMIUM_CONTACT в Telegram")
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Выбор иконки приложения: доступен с Премиумом. */
@Composable
private fun AppIconPicker(isPremium: Boolean, onError: (String) -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Иконка приложения", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isPremium) "Выберите, как RyzikChat выглядит на главном экране" else "Доступно с Премиумом",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            AppIcons.all.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { icon ->
                        val selected = current == icon.key
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (!isPremium) { onError("Иконку можно сменить с Премиумом"); return@clickable }
                                    runCatching { AppIcons.apply(context, icon.key) }
                                        .onSuccess { current = icon.key }
                                        .onFailure { onError("Не получилось сменить иконку") }
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(icon.colors))
                                    .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🦊", fontSize = 26.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(icon.title, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

private class Perk(val icon: ImageVector, val title: String, val text: String)

@Composable
private fun PerkRow(p: Perk, index: Int) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        appear.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); translationX = (1f - appear.value) * 120f }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(PremiumGradient)),
            contentAlignment = Alignment.Center,
        ) { Icon(p.icon, null, tint = Color.White) }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(p.text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
