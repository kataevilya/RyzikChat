package app.ryzik.chat.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AiAction
import app.ryzik.chat.data.AiClient
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.launch

/**
 * AI-помощник: первый запуск просит адрес и ключ, потом предлагает три действия.
 * Ключ хранится только на этом устройстве и не уходит на сервер RyzikChat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(text: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val app = RyzikApp.instance
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var url by remember(settings.aiUrl) { mutableStateOf(settings.aiUrl) }
    var key by remember(settings.aiKey) { mutableStateOf(settings.aiKey) }
    var model by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var showSetup by remember { mutableStateOf(false) }
    val configured = settings.aiUrl.isNotBlank() && settings.aiKey.isNotBlank()

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            app.prefs.update { it.copy(aiUrl = url.trim(), aiKey = key.trim(), aiModel = model.trim().ifBlank { "gpt-4o-mini" }) }
            showSetup = false
            error = null
        }
    }

    fun run(action: AiAction) {
        loading = true
        result = null
        error = null
        scope.launch {
            runCatching {
                AiClient.run(
                    baseUrl = settings.aiUrl.ifBlank { url },
                    key = settings.aiKey.ifBlank { key },
                    model = settings.aiModel.ifBlank { model },
                    action = action,
                    text = text,
                )
            }.onSuccess { result = it }.onFailure { error = it.userMessage() }
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("✨", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI-помощник", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Стиль, эмодзи и сокращение текста",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (!configured || showSetup) {
                Text(
                    if (configured) "Настройки AI" else "Первый запуск: подключите AI",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Подойдёт любой OpenAI-совместимый API. Например, ссылка вида https://api.example.com/v1 и ваш ключ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на API") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API-ключ") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Модель") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Ключ хранится только на этом устройстве. Текст отправляется напрямую на указанный вами адрес.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    if (configured) TextButton(onClick = { showSetup = false }) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = url.isNotBlank() && key.isNotBlank(), onClick = { save() }) { Text("Сохранить") }
                }
            } else {
                Text("Что сделать с текстом?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AiAction.entries.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !loading) { run(action) },
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(action.emoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Text(action.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (!loading) {
                    TextButton(onClick = { showSetup = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Настроить AI")
                    }
                }
            }

            if (loading) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                Text(
                    "AI думает…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            error?.let {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                }
            }

            result?.let { out ->
                Spacer(Modifier.height(16.dp))
                Text("Готовый текст", style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(out, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onApply(text.trimEnd() + "\n\n" + out); onDismiss() }) { Text("Вставить ниже") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(out); onDismiss() }) { Text("Заменить") }
                }
            }
        }
    }
}
