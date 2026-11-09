package app.ryzik.chat.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.ServerStatus
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.formatListTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Версия сервера и его самообновление с GitHub. */
@Composable
fun ServerUpdates(modifier: Modifier) {
    val api = RyzikApp.instance.repo.api
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ServerStatus?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { api.serverStatus() }.onSuccess { status = it }.onFailure { message = it.userMessage() }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Сервер ${status?.version ?: "…"}", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                val s = status
                if (s != null) {
                    Text(
                        when {
                            s.latest == null -> "Новую версию ещё не проверяли"
                            s.latest == s.version -> "Установлена последняя версия"
                            else -> "На GitHub есть версия ${s.latest}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    s.checkedAt?.let { Text("Проверено: ${formatListTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        if (s.autoUpdate) "Автообновление включено: сервер проверяет GitHub раз в 30 минут, перед обновлением сохраняет копию базы в data/backups."
                        else "Автообновление выключено (AUTO_UPDATE=off).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!s.supervised) Text(
                        "Сервер запущен без npm start, поэтому после обновления его нужно перезапустить вручную.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    s.error?.let { Text("Последняя ошибка: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !busy,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = {
                        busy = true; message = null
                        scope.launch {
                            runCatching { api.updateServer() }
                                .onSuccess { r ->
                                    message = when {
                                        !r.updated -> "Обновлений нет, версия ${r.version} последняя"
                                        r.restarting -> "Обновлено до ${r.version}. Сервер перезапускается"
                                        else -> "Обновлено до ${r.version}. Перезапустите сервер"
                                    }
                                    if (r.restarting) delay(8000)
                                }
                                .onFailure { message = it.userMessage() }
                            busy = false
                            reload++
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Проверить и обновить сейчас")
                }
            }
        }
        status?.let { s ->
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ошибки сервера", style = MaterialTheme.typography.titleMedium)
                    Text("Работает без перезапуска: ${s.uptimeSec / 3600} ч ${s.uptimeSec % 3600 / 60} мин", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.errors.isEmpty()) Text("С момента запуска ошибок не было", style = MaterialTheme.typography.bodyMedium)
                    for (e in s.errors) Column {
                        Text("${formatListTime(e.at)} · ${e.method} ${e.path} · код ${e.ref}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        Text(e.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { reload++ }) { Text("Обновить") }
                }
            }
        }
        MassReset()
    }
}
