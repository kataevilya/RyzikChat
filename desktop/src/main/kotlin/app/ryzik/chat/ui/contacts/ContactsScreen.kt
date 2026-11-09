package app.ryzik.chat.ui.contacts

import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.BadgeIcons
import app.ryzik.chat.ui.components.formatLastSeen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Вкладка «Контакты»: ваши контакты и поиск людей, чтобы добавить новых. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(onOpenChat: (String) -> Unit, onOpenProfile: (String) -> Unit) {
    val repo = RyzikApp.instance.repo
    val contacts by repo.contacts.collectAsState()
    val users by repo.users.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<User>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { repo.refreshContacts() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { found = emptyList(); return@LaunchedEffect }
        delay(300)
        found = runCatching { repo.searchUsers(query.trim()) }.getOrDefault(emptyList()).filter { it.id != repo.myId && !it.isService }
    }

    fun open(u: User) {
        scope.launch { runCatching { repo.openDirect(u.id) }.onSuccess { onOpenChat(it.id) }.onFailure { error = it.userMessage() } }
    }

    val q = query.trim()
    val mine = contacts.map { users[it.id] ?: it }.filter {
        q.isEmpty() || it.displayName.contains(q, ignoreCase = true) || it.username.contains(q, ignoreCase = true)
    }
    val others = found.filter { f -> contacts.none { it.id == f.id } }

    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("Контакты", fontWeight = FontWeight.Bold) })
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Найти или добавить по имени, @нику") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch { runCatching { repo.refreshContacts() }; refreshing = false }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "err") {
                    AnimatedVisibility(error != null) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
                if (mine.isNotEmpty()) item(key = "h_mine") { Header(if (q.isEmpty()) "Мои контакты · ${contacts.size}" else "В контактах") }
                items(mine, key = { "c_" + it.id }) { u ->
                    PersonRow(
                        u, repo.avatarUrl(u.avatarFileId),
                        trailing = null,
                        modifier = Modifier.animateItem().combinedClickable(onClick = { open(u) }, onLongClick = { onOpenProfile(u.id) }),
                    )
                }
                if (others.isNotEmpty()) item(key = "h_found") { Header("Люди в RyzikChat") }
                items(others, key = { "f_" + it.id }) { u ->
                    PersonRow(
                        u, repo.avatarUrl(u.avatarFileId),
                        trailing = {
                            FilledTonalIconButton(onClick = {
                                scope.launch { runCatching { repo.setContact(u.id, true) }.onFailure { error = it.userMessage() } }
                            }) { Icon(Icons.Rounded.PersonAdd, "Добавить в контакты") }
                        },
                        modifier = Modifier.animateItem().combinedClickable(onClick = { onOpenProfile(u.id) }),
                    )
                }
                if (mine.isEmpty() && others.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        app.ryzik.chat.ui.components.EmptyIcon(if (q.isEmpty()) Icons.Rounded.Group else Icons.Rounded.SearchOff)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (q.isEmpty()) "Контактов пока нет. Найдите друга через поиск сверху и нажмите «добавить», или добавьте его прямо из чата."
                            else if (q.length < 2) "Введите хотя бы две буквы" else "Никого не нашлось",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PersonRow(u: User, avatarUrl: String?, trailing: (@Composable () -> Unit)?, modifier: Modifier) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                BadgeIcons(u.badges, u.isAdmin, 16.dp, u.isPremium, u.emojiStatus, u.verified)
            }
        },
        supportingContent = {
            Text(
                if (u.online) "в сети" else "@${u.username} · ${formatLastSeen(false, u.lastSeen)}",
                color = if (u.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Avatar(u.displayName, avatarUrl, 48.dp, online = u.online) },
        trailingContent = trailing ?: { if (u.isContact) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.outline) },
        modifier = modifier,
    )
}
