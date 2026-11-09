package app.ryzik.chat.ui.stories

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.data.Chat
import app.ryzik.chat.data.Streak

/** Огонёк виден, если чат уже переписывается хотя бы день подряд. */
fun Streak?.visible(): Boolean = this != null && days > 0

private val FireOrange = Color(0xFFFF7A1A)

/**
 * Огонёк с числом дней. Если сегодня ещё не продлили, он бледнеет и «дышит»:
 * напишите сегодня вдвоём, иначе погаснет в полночь по Москве.
 */
@Composable
fun StreakBadge(streak: Streak?, modifier: Modifier = Modifier, big: Boolean = false, onClick: (() -> Unit)? = null) {
    if (!streak.visible()) return
    streak!!
    val atRisk = !streak.today
    val pulse = if (atRisk) {
        val t = rememberInfiniteTransition(label = "streak")
        t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "streakPulse").value
    } else 1f
    Row(
        modifier
            .clip(CircleShape)
            .background(FireOrange.copy(alpha = if (atRisk) 0.10f else 0.16f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = if (big) 10.dp else 6.dp, vertical = if (big) 4.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔥", fontSize = if (big) 16.sp else 12.sp, modifier = Modifier.graphicsLayer { alpha = pulse })
        Text(
            streak.days.toString(),
            fontSize = if (big) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = FireOrange,
            modifier = Modifier.padding(start = 2.dp).alpha(if (atRisk) 0.7f else 1f),
        )
    }
}

/** Окно с объяснением, как продлить огонёк. */
@Composable
fun StreakInfoDialog(chat: Chat, myId: String?, onDismiss: () -> Unit) {
    val s = chat.streak
    val group = chat.type == "group"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        icon = { Text("🔥", fontSize = 40.sp) },
        title = {
            Text(
                if (s.visible()) "${s!!.days} ${dayWord(s.days)} подряд" else "Огонёк ещё не зажёгся",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (group) "Огонёк продлевается на день, когда в группу за сутки написали хотя бы два разных человека."
                    else "Огонёк продлевается на день, когда за сутки написали вы оба.",
                )
                when {
                    s == null || s.days == 0 && s.todayWriters.isEmpty() -> Text("Напишите сегодня, чтобы зажечь его.")
                    s.today -> Text("Сегодня уже продлили ✅", fontWeight = FontWeight.SemiBold)
                    else -> {
                        val wrote = myId != null && myId in s.todayWriters
                        Text(
                            when {
                                group -> "Сегодня написали: ${s.todayWriters.size} из 2."
                                wrote -> "Вы уже написали, ждём собеседника."
                                s.todayWriters.isNotEmpty() -> "Собеседник уже написал, ваша очередь."
                                else -> "Сегодня ещё никто не писал."
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (s.days > 0) Text("Если не продлить, огонёк погаснет в полночь по Москве.", color = MaterialTheme.colorScheme.error)
                    }
                }
                if ((s?.best ?: 0) > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text("Рекорд: ${s!!.best} ${dayWord(s.best)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

fun dayWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "день"
        m10 in 2..4 && m100 !in 12..14 -> "дня"
        else -> "дней"
    }
}
