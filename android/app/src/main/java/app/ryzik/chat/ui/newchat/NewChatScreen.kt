package app.ryzik.chat.ui.newchat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.BadgeIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Поиск людей по @username или имени. В режиме группы — выбор участников и название. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit, addToChatId: String? = null, startAsGroup: Boolean = false) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var groupMode by remember { mutableStateOf(addToChatId != null || startAsGroup) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<User>() }
    var groupTitle by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(false) }
    var groupUsername by remember { mutableStateOf("") }
    var usernameOk by remember { mutableStateOf(true) }
    var avatar by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        searching = true
        results = runCatching { repo.searchUsers(query) }.getOrElse { error = it.userMessage(); emptyList() }
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    AnimatedContent(groupMode, label = "t") { g ->
                        Text(
                            when {
                                addToChatId != null -> "Добавить участников"
                                g -> "Новая группа"
                                else -> "Новый чат"
                            }
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(groupMode && (selected.isNotEmpty() || (addToChatId == null && groupTitle.isNotBlank())), enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = scaleOut()) {
                FloatingActionButton(onClick = {
                    if (busy) return@FloatingActionButton
                    if (addToChatId == null && groupTitle.isBlank()) { error = "Придумайте название группы"; return@FloatingActionButton }
                    if (addToChatId == null && isPublic && !usernameOk) { error = "Выберите свободный юзернейм или оставьте поле пустым"; return@FloatingActionButton }
                    busy = true
                    scope.launch {
                        runCatching {
                            if (addToChatId != null) {
                                repo.addMembers(addToChatId, selected.map { it.id })
                                onBack()
                            } else {
                                val fileId = avatar?.let { repo.uploadAvatar(it) }
                                val c = repo.createGroup(groupTitle.trim(), selected.map { it.id }, isPublic, avatarFileId = fileId, username = groupUsername.takeIf { isPublic && it.isNotBlank() })
                                onOpenChat(c.id)
                            }
                        }.onFailure { error = it.userMessage() }
                        busy = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                    else Icon(if (addToChatId != null) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, "Готово")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(groupMode && addToChatId == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        app.ryzik.chat.ui.channel.AvatarPicker(groupTitle, avatar?.toString(), 56.dp) { avatar = it }
                        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                        OutlinedTextField(
                            value = groupTitle,
                            onValueChange = { groupTitle = it.take(128) },
                            label = { Text("Название группы") },
                            leadingIcon = { Icon(Icons.Rounded.Group, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    app.ryzik.chat.ui.channel.VisibilitySelector(isPublic, channel = false, onChange = { isPublic = it })
                    if (isPublic) {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        app.ryzik.chat.ui.channel.ChatUsernameField(groupUsername, { groupUsername = it }, onValid = { usernameOk = it })
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Имя или @username") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AnimatedVisibility(selected.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selected, key = { it.id }) { u ->
                        InputChip(
                            selected = true,
                            onClick = { selected.remove(u) },
                            label = { Text(u.displayName) },
                            avatar = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 24.dp) },
                            trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (!groupMode && query.isBlank()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Создать группу") },
                            leadingContent = { CircleIcon { Icon(Icons.Rounded.Group, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) } },
                            modifier = Modifier.clickable { groupMode = true },
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Избранное") },
                            supportingContent = { Text("Заметки и сохранённые сообщения") },
                            leadingContent = { CircleIcon { Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) } },
                            modifier = Modifier.clickable { repo.savedChat()?.let { onOpenChat(it.id) } },
                        )
                    }
                    item {
                        Text(
                            "Найдите друзей по имени пользователя — например, @ryzik",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(results, key = { it.id }) { u ->
                    val isSelected = selected.any { it.id == u.id }
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.displayName)
                                BadgeIcons(u.badges, u.isAdmin, verified = u.verified)
                            }
                        },
                        supportingContent = { Text("@${u.username}") },
                        leadingContent = {
                            Box {
                                Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 48.dp, online = u.online)
                                androidx.compose.animation.AnimatedVisibility(
                                    isSelected,
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    enter = scaleIn(spring(Spring.DampingRatioHighBouncy)),
                                    exit = scaleOut(),
                                ) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.animateItem().clickable {
                            if (groupMode) {
                                if (isSelected) selected.removeAll { it.id == u.id } else selected.add(u)
                            } else scope.launch {
                                runCatching { repo.openDirect(u.id) }.onSuccess { onOpenChat(it.id) }.onFailure { error = it.userMessage() }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { content() }
}
