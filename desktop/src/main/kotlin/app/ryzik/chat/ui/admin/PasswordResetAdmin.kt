package app.ryzik.chat.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.PasswordResetResult
import app.ryzik.chat.crypto.E2E
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.launch

/**
 * Админ-панель → «Сброс пароля».
 * Меняет пароль обычного пользователя, удаляет 2FA и завершает все сеансы.
 * Аккаунты администраторов защищены.
 */
@Composable
fun PasswordResetAdmin(padding: PaddingValues) {
    val api = RyzikApp.instance.repo.api
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var keepHistory by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PasswordResetResult?>(null) }

    fun reset() {
        confirm = false
        busy = true
        error = null
        result = null
        scope.launch {
            runCatching {
                val name = username.trim().removePrefix("@")
                if (name.isBlank()) throw IllegalArgumentException("Введите юзернейм")
                val found = api.adminUsers(name, all = true).firstOrNull { it.username.equals(name, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Пользователь @$name не найден")
                if (found.isAdmin) throw IllegalArgumentException("Пароль администратора менять нельзя")
                if (keepHistory) {
                    if (oldPassword.isBlank()) throw IllegalArgumentException("Введите старый пароль, чтобы сохранить историю")
                    val target = api.adminUserKey(found.id)
                    val oldKeys = E2E.derivePasswordKeys(target.username, oldPassword)
                    val privateKey = runCatching { E2E.openPrivateKey(target.encryptedPrivateKey, oldKeys.vaultKey) }
                        .getOrElse { throw IllegalArgumentException("Старый пароль не подходит") }
                    val fresh = generatePassword()
                    val newKeys = E2E.derivePasswordKeys(target.username, fresh)
                    api.resetPassword(found.id, newKeys.authKey, E2E.sealPrivateKey(privateKey, newKeys.vaultKey))
                    PasswordResetResult(target.username, fresh)
                } else {
                    api.resetPassword(found.id)
                }
            }.onSuccess { result = it }.onFailure { error = it.userMessage() }
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Сброс пароля", style = MaterialTheme.typography.titleLarge)
            Text(
                "Введите @username обычного пользователя. Пароль и 2FA будут сброшены, все сеансы завершатся, а в служебный чат придёт уведомление.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(40) },
                label = { Text("Юзернейм") },
                placeholder = { Text("@username") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Сохранить старые сообщения", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Нужен старый пароль пользователя. Без него история станет недоступна.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keepHistory, onCheckedChange = { keepHistory = it })
            }
        }
        if (keepHistory) item {
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("Старый пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                enabled = username.isNotBlank() && (!keepHistory || oldPassword.isNotBlank()) && !busy,
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Сбрасываем…" else "Сбросить пароль и 2FA") }
        }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    if (keepHistory) "Аккаунты администраторов менять нельзя — даже главного. Старый пароль расшифрует ключ, а новый пароль запечатает его заново: история сохранится. Новый пароль показывается один раз." else "Аккаунты администраторов менять нельзя — даже главного. Без старого пароля будет создан новый ключ, и старые сообщения станут недоступны. Новый пароль показывается один раз.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        result?.let { r ->
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Новый пароль для @${r.username}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        SelectionContainer {
                            Text(
                                r.password ?: "",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 26.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { clipboard.setText(AnnotatedString(r.password ?: "")) }) { Text("Скопировать") }
                            Spacer(Modifier.width(10.dp))
                            Text("Показывается только сейчас", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Сбросить пароль?") },
            text = {
                Text(
                    if (keepHistory) "У @${username.trim().removePrefix("@")} будут сброшены пароль и 2FA, все сеансы завершатся. Старый пароль расшифрует историю, новый — запечатает её заново. Продолжить?" else "У @${username.trim().removePrefix("@")} будут сброшены пароль и 2FA, все сеансы завершатся, а ключ шифрования будет заменён. Старые сообщения станут недоступны. Продолжить?",
                )
            },
            confirmButton = {
                TextButton(onClick = { reset() }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}


private fun generatePassword(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    val random = java.security.SecureRandom()
    return (1..16).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
}
