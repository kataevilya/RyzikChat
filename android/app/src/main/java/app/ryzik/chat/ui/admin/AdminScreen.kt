package app.ryzik.chat.ui.admin

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.Badge
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.BadgeChip
import app.ryzik.chat.ui.components.BadgeIcons
import app.ryzik.chat.ui.components.parseColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AdminTabs = listOf("Бейджи", "Пользователи", "NFT", "Группы и каналы", "Журнал", "Сервер")

private val BadgeColors = listOf("#FF8A3D", "#6750A4", "#00A3A3", "#2E6BE6", "#43A047", "#E91E63", "#FFB300", "#8D6E63")
private val BadgeEmoji = listOf("⭐", "💎", "🔥", "🏆", "🛠️", "🎨", "🧪", "❤️", "🚀", "👑", "🦊", "🎮")

/** Админ-панель: создание бейджей и выдача их пользователям. Видна только администраторам. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var badges by remember { mutableStateOf<List<Badge>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<User>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Badge?>(null) }
    var tab by remember { mutableStateOf(0) }

    suspend fun reload() { badges = runCatching { repo.badges() }.getOrElse { error = it.userMessage(); badges } }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(query) {
        if (query.isBlank()) { found = emptyList(); return@LaunchedEffect }
        delay(300)
        found = runCatching { repo.searchUsers(query) }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Админ-панель") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                )
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(AdminTabs.size) { i ->
                        val sel = tab == i
                        Text(
                            AdminTabs[i],
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { tab = i }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Новый бейдж") })
        },
    ) { padding ->
        when (tab) {
            1 -> { UsersModeration(Modifier.padding(padding), onOpenProfile); return@Scaffold }
            2 -> { NftCreator(Modifier.padding(padding)); return@Scaffold }
            3 -> { ChatsModeration(Modifier.padding(padding)); return@Scaffold }
            4 -> { ModerationLog(Modifier.padding(padding)); return@Scaffold }
            5 -> { ServerUpdates(Modifier.padding(padding)); return@Scaffold }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Бейджи видны рядом с именем пользователя в чатах и профиле. Выдавать и снимать их можете только вы и другие администраторы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item { Text("Бейджи", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (badges.isEmpty()) item {
                Text("Пока ни одного. Нажмите «Новый бейдж».", Modifier.padding(horizontal = 16.dp))
            }
            items(badges, key = { it.id }) { b ->
                ListItem(
                    headlineContent = { BadgeChip(b) },
                    supportingContent = { if (b.description.isNotBlank()) Text(b.description) },
                    trailingContent = { IconButton(onClick = { toDelete = b }) { Icon(Icons.Rounded.Delete, "Удалить") } },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                Text("Выдать бейдж", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Найти пользователя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Text(
                    "Откройте профиль пользователя и отметьте нужные бейджи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(found, key = { "u" + it.id }) { u ->
                ListItem(
                    headlineContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text(u.displayName); BadgeIcons(u.badges, u.isAdmin, verified = u.verified) } },
                    supportingContent = { Text("@${u.username}") },
                    leadingContent = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 44.dp) },
                    modifier = Modifier.animateItem().clickable { onOpenProfile(u.id) },
                )
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
        }
    }

    if (creating) {
        var emoji by remember { mutableStateOf(BadgeEmoji.first()) }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(BadgeColors.first()) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Новый бейдж") },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BadgeChip(Badge("preview", emoji, title.ifBlank { "Название" }, description, color))
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(BadgeEmoji) { e ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                    OutlinedTextField(emoji, { emoji = it.take(8) }, label = { Text("Или свой эмодзи") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(title, { title = it.take(40) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(description, { description = it.take(200) }, label = { Text("Описание (необязательно)") }, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BadgeColors) { c ->
                            val size by animateDpAsState(if (c == color) 40.dp else 32.dp, spring(Spring.DampingRatioMediumBouncy), label = "c")
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(size).clip(CircleShape).background(parseColor(c)).clickable { color = c })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && emoji.isNotBlank(), onClick = {
                    creating = false
                    scope.launch {
                        runCatching { repo.createBadge(emoji, title.trim(), description.trim(), color) }.onFailure { error = it.userMessage() }
                        reload()
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Отмена") } },
        )
    }

    toDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить бейдж «${b.title}»?") },
            text = { Text("Он пропадёт у всех, кому был выдан.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { runCatching { repo.deleteBadge(b.id) }; reload() }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}
