package app.ryzik.chat.ui.channel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue

/** Аватарка, по нажатию на которую выбирается картинка из галереи. */
@Composable
fun AvatarPicker(name: String, url: String?, size: Dp, busy: Boolean = false, enabled: Boolean = true, onPicked: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(uri) }
    Box(
        Modifier
            .size(size)
            .clip(app.ryzik.chat.ui.components.AvatarShape)
            .clickable(enabled = enabled && !busy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    ) {
        Avatar(name.ifBlank { "?" }, url, size)
        if (enabled) Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.32f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(size * 0.2f), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.CameraAlt, "Выбрать фото", Modifier.size(size * 0.18f), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Выбор: публичный (ищется, вступить может любой) или частный (только по ссылке-приглашению). */
@Composable
fun VisibilitySelector(isPublic: Boolean, channel: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val what = if (channel) "канал" else "группу"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VisibilityOption(
            selected = isPublic,
            icon = Icons.Rounded.Public,
            title = if (channel) "Публичный канал" else "Публичная группа",
            text = "Любой найдёт $what через поиск и сможет ${if (channel) "подписаться" else "вступить"}.",
        ) { onChange(true) }
        VisibilityOption(
            selected = !isPublic,
            icon = Icons.Rounded.Lock,
            title = if (channel) "Частный канал" else "Частная группа",
            text = "В поиске не виден. ${if (channel) "Подписаться" else "Вступить"} можно только по вашей ссылке-приглашению.",
        ) { onChange(false) }
    }
}

@Composable
private fun VisibilityOption(selected: Boolean, icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "b")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface, label = "bg")
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.background(bg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ссылка-приглашение, которую можно отправить друзьям. */
fun inviteLink(code: String) = "ryzik://join/$code"

/** Ссылка на открытую группу или канал по @юзернейму. */
fun usernameLink(username: String) = "ryzik://c/$username"

/**
 * Достаёт код приглашения из ссылки ryzik://join/<код> или из самого кода.
 * Для ссылки ryzik://c/<юзернейм> возвращает «@юзернейм».
 */
fun parseInviteCode(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (t.contains("://c/")) {
        val name = t.substringAfter("://c/").substringBefore('?').trim('/', ' ')
        return if (name.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}"))) "@$name" else null
    }
    val code = t.substringAfter("join/", t).substringBefore('?').trim('/', ' ')
    return code.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,64}")) }
}

fun membersText(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "участник"
        mod10 in 2..4 && mod100 !in 12..14 -> "участника"
        else -> "участников"
    }
    return "$n $word"
}

/** Окно приглашения: показывает группу или канал по ссылке и даёт вступить. */
@Composable
fun InviteDialog(code: String, onDismiss: () -> Unit, onOpenChat: (String) -> Unit) {
    val repo = app.ryzik.chat.RyzikApp.instance.repo
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var chat by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<app.ryzik.chat.data.Chat?>(null) }
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(code) {
        runCatching { repo.invitePreviewAny(code) }.onSuccess { chat = it }.onFailure { error = it.userMessage() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val c = chat
            if (c != null) Avatar(c.title, repo.avatarUrl(c.avatarFileId), 72.dp)
            else if (error == null) CircularProgressIndicator()
        },
        title = { Text(chat?.title ?: if (error != null) "Приглашение" else "Загрузка…") },
        text = {
            val c = chat
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (c != null) {
                    Text(
                        (if (c.type == "channel") app.ryzik.chat.ui.chats.subscribersText(c.memberCount) else membersText(c.memberCount)) +
                            (if (c.isPublic) "" else " · частн${if (c.type == "channel") "ый канал" else "ая группа"}") +
                            (c.username?.let { " · @$it" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(c.description, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val c = chat
            if (c != null) androidx.compose.material3.Button(enabled = !busy, onClick = {
                if (c.myRole != null) onOpenChat(c.id) else {
                    busy = true
                    scope.launch {
                        runCatching { repo.joinAny(code, c) }
                            .onSuccess { onOpenChat(it.id) }
                            .onFailure { error = it.userMessage() }
                        busy = false
                    }
                }
            }) {
                Text(
                    when {
                        c.myRole != null -> "Открыть"
                        c.type == "channel" -> "Подписаться"
                        else -> "Вступить"
                    }
                )
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/**
 * Поле @юзернейма для открытой группы или канала с проверкой, свободен ли он.
 * [onValid] получает true, когда поле пустое или юзернейм свободен.
 */
@Composable
fun ChatUsernameField(value: String, onChange: (String) -> Unit, chatId: String? = null, onValid: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    val repo = app.ryzik.chat.RyzikApp.instance.repo
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Boolean, String>?>(null) }
    androidx.compose.runtime.LaunchedEffect(value) {
        if (value.isBlank()) { status = null; onValid(true); return@LaunchedEffect }
        onValid(false)
        kotlinx.coroutines.delay(400)
        val r = runCatching { repo.api.checkChatUsername(value, chatId) }.getOrNull()
        status = when {
            r == null -> false to "Не удалось проверить"
            r.ok -> true to "@$value свободен"
            else -> false to (r.message ?: "Занят")
        }
        onValid(r?.ok == true)
    }
    Column(modifier) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.take(32)) },
            label = { Text("Публичная ссылка") },
            prefix = { Text("@") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        val st = status
        Text(
            st?.second ?: "По юзернейму вас найдут в поиске и откроют по ссылке ryzik://c/юзернейм. 5–32 символа: латиница, цифры и _.",
            style = MaterialTheme.typography.bodySmall,
            color = when (st?.first) {
                true -> androidx.compose.ui.graphics.Color(0xFF2FBF71)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}
