package app.ryzik.chat.ui.settings

import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import kotlinx.coroutines.launch

/** Список заблокированных с возможностью разблокировать. */
@Composable
fun BlockedSection() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<User>?>(null) }
    LaunchedEffect(Unit) { list = runCatching { repo.api.blocks() }.getOrDefault(emptyList()) }
    val l = list ?: return
    if (l.isEmpty()) {
        Text(
            "Никого. Заблокировать можно в личном чате или профиле.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    l.forEach { u ->
        ListItem(
            headlineContent = { Text(u.displayName) },
            supportingContent = { Text("@${u.username}") },
            leadingContent = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 40.dp) },
            trailingContent = {
                TextButton(onClick = {
                    scope.launch { runCatching { repo.setBlocked(u.id, false) }.onSuccess { list = l.filterNot { it.id == u.id } } }
                }) { Text("Разблокировать") }
            },
        )
    }
}

/** Шрифт эмодзи на ПК берётся из системы. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun EmojiFontSection(enabled: Boolean) {}


/** Двухэтапная проверка: дополнительный пароль, который спрашивается при входе на новом устройстве. */
@Composable
fun TwoFactorSection() {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? app.ryzik.chat.data.AuthState.LoggedIn)?.me ?: return
    var dialog by remember { mutableStateOf<String?>(null) } // "set" | "off"
    var info by remember { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(if (me.has2fa) "Включена" else "Выключена") },
        supportingContent = {
            Text(
                if (me.has2fa) "При входе на новом устройстве спросим дополнительный пароль" +
                    (me.twofaHint.takeIf { it.isNotBlank() }?.let { ". Подсказка: $it" } ?: "")
                else "Включите, чтобы для входа кроме пароля нужен был ещё один, известный только вам",
            )
        },
        leadingContent = { RoundIcon(Icons.Rounded.Shield, if (me.has2fa) SettingsColors.Green else SettingsColors.Gray) },
        modifier = Modifier.clickable { dialog = "set" },
    )
    if (me.has2fa) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "set" }) { Text("Сменить пароль") }
            TextButton(onClick = { dialog = "off" }) { Text("Выключить", color = MaterialTheme.colorScheme.error) }
        }
    }
    info?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }

    if (dialog != null) {
        val scope = rememberCoroutineScope()
        val off = dialog == "off"
        var account by remember { mutableStateOf("") }
        var current by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf(me.twofaHint) }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val pw = androidx.compose.ui.text.input.PasswordVisualTransformation()
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        AlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.Shield, null) },
            title = { Text(if (off) "Выключить проверку?" else if (me.has2fa) "Новый дополнительный пароль" else "Двухэтапная проверка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (off) {
                        OutlinedTextField(current, { current = it; err = null }, label = { Text("Дополнительный пароль") }, singleLine = true, visualTransformation = pw, shape = shape)
                    } else {
                        OutlinedTextField(account, { account = it; err = null }, label = { Text("Пароль от аккаунта") }, singleLine = true, visualTransformation = pw, shape = shape)
                        if (me.has2fa) OutlinedTextField(current, { current = it; err = null }, label = { Text("Текущий дополнительный") }, singleLine = true, visualTransformation = pw, shape = shape)
                        OutlinedTextField(new, { new = it; err = null }, label = { Text("Дополнительный пароль") }, singleLine = true, visualTransformation = pw, shape = shape)
                        OutlinedTextField(repeat, { repeat = it; err = null }, label = { Text("Повторите") }, singleLine = true, visualTransformation = pw, shape = shape)
                        OutlinedTextField(hint, { hint = it.take(64) }, label = { Text("Подсказка (необязательно)") }, singleLine = true, shape = shape)
                    }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (!off && new.length < 4) { err = "Минимум 4 символа"; return@TextButton }
                    if (!off && new != repeat) { err = "Пароли не совпадают"; return@TextButton }
                    busy = true
                    scope.launch {
                        runCatching {
                            if (off) repo.disable2fa(current) else repo.set2fa(account, current.takeIf { me.has2fa }, new, hint)
                        }.onSuccess {
                            info = if (off) "Двухэтапная проверка выключена" else "Готово. Запомните пароль: без него войти на новом устройстве не получится."
                            dialog = null
                        }.onFailure { err = it.userMessage() }
                        busy = false
                    }
                }) { Text(if (off) "Выключить" else "Сохранить") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Отмена") } },
        )
    }
}
