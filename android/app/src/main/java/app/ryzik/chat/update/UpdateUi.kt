package app.ryzik.chat.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Тихая проверка при запуске и окно «Доступно обновление» поверх приложения. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    LaunchedEffect(Unit) { Updater.check(context, silent = true) }

    when (val s = state) {
        is UpdateState.Available -> if (!Updater.wasDismissed(context, s.info)) {
            UpdateDialog(
                s.info,
                progress = null,
                onUpdate = { scope.launch { Updater.download(context, s.info) } },
                onLater = { Updater.dismiss(context, s.info) },
            )
        }
        is UpdateState.Downloading -> UpdateDialog(s.info, progress = s.progress, onUpdate = {}, onLater = {})
        else -> {}
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, progress: Float?, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (progress == null) onLater() },
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Новая версия ${info.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (progress != null) {
                    Text("Загрузка ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (progress == null) Button(onClick = onUpdate) { Text("Обновить") }
        },
        dismissButton = {
            if (progress == null) TextButton(onClick = onLater) { Text("Позже") }
        },
    )
}
