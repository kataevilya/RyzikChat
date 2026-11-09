package app.ryzik.chat.ui.channel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.launch

/** Создание канала: название и описание. Писать в канал может только владелец и его админы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChannelScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var usernameOk by remember { mutableStateOf(true) }
    var avatar by remember { mutableStateOf<android.net.Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0.3f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "pop")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = { Text("Новый канал") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (busy) return@FloatingActionButton
                if (title.isBlank()) { error = "Придумайте название канала"; return@FloatingActionButton }
                if (isPublic && !usernameOk) { error = "Выберите свободный юзернейм или оставьте поле пустым"; return@FloatingActionButton }
                busy = true
                scope.launch {
                    runCatching {
                        val fileId = avatar?.let { repo.uploadAvatar(it) }
                        repo.createChannel(title.trim(), description.trim(), isPublic, fileId, username.takeIf { isPublic && it.isNotBlank() })
                    }
                        .onSuccess { onOpenChat(it.id) }
                        .onFailure { error = it.userMessage() }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else Icon(Icons.Rounded.Check, "Создать")
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.graphicsLayer { scaleX = pop; scaleY = pop }) {
                if (title.isBlank() && avatar == null) {
                    AvatarPicker("", null, 96.dp) { avatar = it }
                    Icon(Icons.Rounded.Campaign, null, Modifier.size(56.dp).align(Alignment.Center), tint = androidx.compose.ui.graphics.Color.White)
                } else AvatarPicker(title, avatar?.toString(), 96.dp) { avatar = it }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(128); error = null },
                label = { Text("Название канала") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(500) },
                label = { Text("Описание (необязательно)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            VisibilitySelector(isPublic, channel = true, onChange = { isPublic = it })
            if (isPublic) ChatUsernameField(username, { username = it }, onValid = { usernameOk = it })
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            Text(
                "В канал пишете вы и назначенные вами админы. Остальные подписываются, читают и ставят реакции. " +
                    "Сообщения в каналах не шифруются сквозным шифрованием, даже в частных.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(72.dp))
        }
    }
}
