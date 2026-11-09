package app.ryzik.chat.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.launch

private const val ConfirmWord = "СБРОС"

/**
 * Массовый сброс: забрать FLUX, админки, галочки и бейджи у всех, кроме главного администратора.
 * Галочки остаются у общего чата и канала новостей. Сервер выполнит его только для главного админа.
 */
@Composable
fun MassReset() {
    val api = RyzikApp.instance.repo.api
    val scope = rememberCoroutineScope()
    var flux by remember { mutableStateOf(true) }
    var admins by remember { mutableStateOf(true) }
    var verified by remember { mutableStateOf(true) }
    var badges by remember { mutableStateOf(true) }
    var word by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Массовый сброс", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(
                "Забирает у всех пользователей, кроме главного администратора. Галочки остаются у общего чата и канала новостей. " +
                    "Перед сбросом сервер сохраняет копию базы в data/backups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResetOption("Забрать все FLUX", flux) { flux = it }
            ResetOption("Забрать все админки", admins) { admins = it }
            ResetOption("Забрать галочки", verified) { verified = it }
            ResetOption("Забрать бейджи", badges) { badges = it }
            OutlinedTextField(
                value = word,
                onValueChange = { word = it.trim().uppercase() },
                label = { Text("Введите $ConfirmWord для подтверждения") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            Button(
                enabled = !busy && word == ConfirmWord && (flux || admins || verified || badges),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 4.dp),
                onClick = { asking = true },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Сбросить")
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Точно сбросить у всех?") },
            text = { Text("Это коснётся всех пользователей, кроме вас. Вернуть можно только из копии базы.") },
            confirmButton = {
                TextButton(onClick = {
                    asking = false; busy = true; message = null
                    scope.launch {
                        runCatching { api.massReset(flux, admins, verified, badges, word) }
                            .onSuccess { r ->
                                failed = false
                                message = listOfNotNull(
                                    if (flux) "FLUX: у ${r.flux} польз. (${r.fluxTotal})" else null,
                                    if (admins) "админки: ${r.admins}" else null,
                                    if (verified) "галочки: ${r.verifiedUsers} польз., ${r.verifiedChats} чатов" else null,
                                    if (badges) "бейджи: ${r.badges}" else null,
                                ).joinToString(", ", prefix = "Готово. Забрано — ") + ". Копия базы: ${r.backup}"
                                word = ""
                            }
                            .onFailure { failed = true; message = it.userMessage() }
                        busy = false
                    }
                }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ResetOption(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
