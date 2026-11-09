package app.ryzik.chat.ui.admin

import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import app.ryzik.chat.ui.flux.formatFlux
import app.ryzik.chat.ui.flux.FluxIcon
import app.ryzik.chat.ui.flux.FluxAmount
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AdminChat
import app.ryzik.chat.data.DeviceBan
import app.ryzik.chat.data.FOREVER_UNTIL
import app.ryzik.chat.data.ModerationLogEntry
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.chats.SearchPill
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.VerifiedMark
import app.ryzik.chat.ui.components.formatListTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Сроки бана и ограничения: подпись и количество дней (0 — навсегда). */
private val Durations = listOf("1 час" to 1.0 / 24, "1 день" to 1.0, "7 дней" to 7.0, "30 дней" to 30.0, "Навсегда" to 0.0)

private fun untilText(until: Long?): String = when {
    until == null -> ""
    until >= FOREVER_UNTIL -> "навсегда"
    else -> "до " + SimpleDateFormat("d MMM HH:mm", Locale("ru")).format(Date(until))
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Выдать или снять галочку верификации. */
@Composable
private fun VerifyButton(verified: Boolean, onChange: (Boolean) -> Unit) {
    if (verified) OutlinedButton(onClick = { onChange(false) }, shape = RoundedCornerShape(16.dp)) {
        VerifiedMark(18.dp)
        Spacer(Modifier.width(6.dp))
        Text("Снять галочку")
    } else FilledTonalButton(onClick = { onChange(true) }, shape = RoundedCornerShape(16.dp)) {
        VerifiedMark(18.dp)
        Spacer(Modifier.width(6.dp))
        Text("Выдать галочку")
    }
}

@Composable
private fun ModCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

// ===================== Пользователи =====================

@Composable
fun UsersModeration(modifier: Modifier, onOpenProfile: (String) -> Unit) {
    val api = RyzikApp.instance.repo.api
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<Pair<User, String>?>(null) } // "ban" | "restrict" | "delete" | "device"
    var grant by remember { mutableStateOf<User?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var deviceBans by remember { mutableStateOf<List<DeviceBan>>(emptyList()) }
    var info by remember { mutableStateOf<String?>(null) }
    // Без поиска: все аккаунты или только наказанные.
    var showAll by remember { mutableStateOf(true) }

    LaunchedEffect(query, reload, showAll) {
        if (query.isNotBlank()) delay(300)
        runCatching { api.adminUsers(query.trim(), all = showAll) }.onSuccess { users = it; error = null }.onFailure { error = it.userMessage() }
        if (query.isBlank()) runCatching { api.deviceBans() }.onSuccess { deviceBans = it }
    }
    fun update(u: User) { users = users.map { if (it.id == u.id) u else it } }
    fun run(block: suspend () -> User) {
        scope.launch { runCatching { block() }.onSuccess { update(it) }.onFailure { error = it.userMessage() } }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SearchPill(query, { query = it }, "Имя или @username", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (query.isBlank()) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("Все аккаунты") })
                FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("Наказанные") })
            }
            Text(
                when {
                    query.isNotBlank() -> "Результаты поиска"
                    showAll -> "Все аккаунты: ${users.size}. Сначала новые."
                    else -> "Заблокированные и ограниченные."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
        items(users, key = { it.id }) { u ->
            ModCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile(u.id) }) {
                    Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (u.verified) { Spacer(Modifier.width(4.dp)); VerifiedMark(16.dp) }
                        }
                        Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (u.isAdmin) StatusChip("админ", MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    FluxAmount(u.flux, iconSize = 16.dp)
                }
                if (u.bannedUntil != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Заблокирован ${untilText(u.bannedUntil)}" + u.banReason.takeIf { it.isNotBlank() }?.let { ". $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (u.restrictedUntil != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Ограничен ${untilText(u.restrictedUntil)}" + u.restrictReason.takeIf { it.isNotBlank() }?.let { ". $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9F43))
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { grant = u }, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Выдать или забрать FLUX")
                }
                Spacer(Modifier.height(6.dp))
                VerifyButton(u.verified) { v -> run { api.setUserVerified(u.id, v) } }
                if (!u.isAdmin) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (u.bannedUntil != null) OutlinedButton(onClick = { run { api.unbanUser(u.id) } }) { Text("Разбанить") }
                        else OutlinedButton(onClick = { action = u to "ban" }) { Text("Забанить", color = MaterialTheme.colorScheme.error) }
                        if (u.restrictedUntil != null) OutlinedButton(onClick = { run { api.unrestrictUser(u.id) } }) { Text("Снять ограничение") }
                        else if (u.bannedUntil == null) OutlinedButton(onClick = { action = u to "restrict" }) { Text("Ограничить") }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { action = u to "device" }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.PhonelinkLock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Бан по устройству и Wi-Fi", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { action = u to "delete" },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) {
                        Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить с сообщениями")
                    }
                }
            }
        }
        if (query.isBlank() && deviceBans.isNotEmpty()) {
            item {
                Text(
                    "Заблокированные устройства и сети",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(deviceBans, key = { "d" + it.id }) { b ->
                ModCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (b.kind == "ip") Icons.Rounded.Wifi else Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (b.kind == "ip") "Сеть (IP) " else "Устройство ") + b.value.take(24),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(b.username.takeIf { it.isNotBlank() }?.let { "@$it" }, b.reason.takeIf { it.isNotBlank() }, formatListTime(b.createdAt)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { api.removeDeviceBan(b.id) }
                                    .onSuccess { deviceBans = deviceBans.filter { it.id != b.id } }
                                    .onFailure { error = it.userMessage() }
                            }
                        }) { Text("Снять") }
                    }
                }
            }
        }
    }

    grant?.let { u ->
        FluxGrantDialog(u, onDismiss = { grant = null }) { amount, note ->
            grant = null
            run { api.grantFlux(u.id, amount, note) }
        }
    }
    action?.takeIf { it.second == "delete" }?.let { (u, _) ->
        PunishDialog(
            title = "Удалить @${u.username}?",
            text = "Аккаунт будет удалён навсегда вместе со всеми его сообщениями во всех чатах. Отменить это нельзя.",
            icon = Icons.Rounded.DeleteForever,
            confirm = "Удалить",
            withDuration = false,
            onDismiss = { action = null },
        ) { _, reason ->
            action = null
            scope.launch {
                runCatching { api.deleteUserAsAdmin(u.id, reason) }
                    .onSuccess { r -> users = users.filter { it.id != u.id }; error = null; info = "@${u.username} удалён, стёрто сообщений: ${r.deletedMessages}" }
                    .onFailure { error = it.userMessage() }
            }
        }
    }
    action?.takeIf { it.second == "device" }?.let { (u, _) ->
        DeviceBanDialog(u, onDismiss = { action = null }) { reason, byDevice, byIp ->
            action = null
            scope.launch {
                runCatching { api.deviceBanUser(u.id, reason, byDevice, byIp) }
                    .onSuccess { r ->
                        update(r.user); error = null; reload++
                        info = "@${u.username} забанен навсегда. Устройств: ${r.devices}, сетей: ${r.ips}"
                    }
                    .onFailure { error = it.userMessage() }
            }
        }
    }
    action?.takeIf { it.second == "ban" || it.second == "restrict" }?.let { (u, kind) ->
        PunishDialog(
            title = if (kind == "ban") "Забанить @${u.username}?" else "Ограничить @${u.username}?",
            text = if (kind == "ban") "Аккаунт выйдет со всех устройств и не сможет войти до конца срока."
            else "Сможет читать чаты, но не сможет писать, создавать группы и каналы и загружать файлы.",
            icon = if (kind == "ban") Icons.Rounded.Block else Icons.Rounded.SpeakerNotesOff,
            confirm = if (kind == "ban") "Забанить" else "Ограничить",
            onDismiss = { action = null },
        ) { days, reason ->
            action = null
            run { if (kind == "ban") api.banUser(u.id, days, reason) else api.restrictUser(u.id, days, reason) }
        }
    }
}

/** Бан по устройству и сети: с них больше не войти и не зарегистрировать новый аккаунт. */
@Composable
private fun DeviceBanDialog(u: User, onDismiss: () -> Unit, onConfirm: (String, Boolean, Boolean) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var byDevice by remember { mutableStateOf(true) }
    var byIp by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.PhonelinkLock, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Бан @${u.username} по устройству") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Аккаунт забанится навсегда. С его устройств и сетей нельзя будет войти ни в какой аккаунт и создать новый.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { byDevice = !byDevice }) {
                    Checkbox(byDevice, { byDevice = it })
                    Text("Устройство (телефон)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { byIp = !byIp }) {
                    Checkbox(byIp, { byIp = it })
                    Text("Wi-Fi и сеть (IP)")
                }
                if (byIp) Text(
                    "Бан по IP не пустит и других людей из той же сети.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("Причина (её увидит пользователь)") }, shape = RoundedCornerShape(16.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = byDevice || byIp, onClick = { onConfirm(reason.trim(), byDevice, byIp) }) {
                Text("Забанить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор срока и причины наказания. Срок в днях, 0 — навсегда. */
@Composable
private fun PunishDialog(
    title: String,
    text: String,
    icon: ImageVector,
    confirm: String,
    withDuration: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
) {
    var duration by remember { mutableIntStateOf(1) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(icon, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (withDuration) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Durations.size) { i ->
                        val sel = duration == i
                        Text(
                            Durations[i].first,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { duration = i }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("Причина (её увидит пользователь)") }, shape = RoundedCornerShape(16.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Durations[duration].second, reason.trim()) }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ===================== Группы и каналы =====================

@Composable
fun ChatsModeration(modifier: Modifier) {
    val api = RyzikApp.instance.repo.api
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<AdminChat>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<Pair<AdminChat, String>?>(null) } // "ban" | "delete"
    // Без поиска: all — все, group — группы, channel — каналы, marked — заблокированные и с галочкой.
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(query, filter) {
        if (query.isNotBlank()) delay(300)
        runCatching { api.adminChats(query.trim(), all = filter != "marked", type = filter.takeIf { it == "group" || it == "channel" }) }
            .onSuccess { chats = it; error = null }.onFailure { error = it.userMessage() }
    }
    fun run(block: suspend () -> AdminChat) {
        scope.launch {
            runCatching { block() }.onSuccess { c -> chats = chats.map { if (it.id == c.id) c else it } }.onFailure { error = it.userMessage() }
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SearchPill(query, { query = it }, "Название группы или канала", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (query.isBlank()) androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = listOf("all" to "Все", "group" to "Группы", "channel" to "Каналы", "marked" to "Заблокированные и с галочкой")
                items(options.size) { i ->
                    val (key, label) = options[i]
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
            Text(
                if (query.isNotBlank()) "Результаты поиска" else "Найдено: ${chats.size}. Поиск по названию или @юзернейму находит и частные.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
        items(chats, key = { it.id }) { c ->
            ModCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.title, repo.avatarUrl(c.avatarFileId), 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (c.type == "channel") Icons.Rounded.Campaign else Icons.Rounded.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (c.verified) { Spacer(Modifier.width(4.dp)); VerifiedMark(16.dp) }
                        }
                        c.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        Text(
                            (if (c.type == "channel") "Канал" else "Группа") + " · ${c.memberCount} уч. · " + (if (c.isPublic) "открытый" else "частный") +
                                (c.owner?.let { " · владелец @${it.username}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (c.banned) StatusChip("заблокирован", MaterialTheme.colorScheme.error)
                }
                if (c.banned && c.banReason.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Причина: ${c.banReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                VerifyButton(c.verified) { v -> run { api.setChatVerified(c.id, v) } }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.banned) OutlinedButton(onClick = { run { api.unbanChat(c.id) } }) { Text("Разблокировать") }
                    else OutlinedButton(onClick = { action = c to "ban" }) { Text("Заблокировать") }
                    OutlinedButton(onClick = { action = c to "delete" }) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    action?.let { (c, kind) ->
        val what = if (c.type == "channel") "канал" else "группу"
        PunishDialog(
            title = if (kind == "ban") "Заблокировать $what «${c.title}»?" else "Удалить $what «${c.title}»?",
            text = if (kind == "ban") "Писать и вступать будет нельзя, в поиске не появится. Участники увидят причину. Можно разблокировать."
            else "Чат и все сообщения удалятся у всех участников навсегда. Это нельзя отменить.",
            icon = if (kind == "ban") Icons.Rounded.Gavel else Icons.Rounded.Delete,
            confirm = if (kind == "ban") "Заблокировать" else "Удалить навсегда",
            withDuration = false,
            onDismiss = { action = null },
        ) { _, reason ->
            action = null
            if (kind == "ban") run { api.banChat(c.id, reason) }
            else scope.launch {
                runCatching { api.deleteChatAsAdmin(c.id, reason) }
                    .onSuccess { chats = chats.filterNot { it.id == c.id } }
                    .onFailure { error = it.userMessage() }
            }
        }
    }
}

// ===================== Журнал =====================

private fun actionText(e: ModerationLogEntry): String {
    val target = when (e.targetType) {
        "user" -> "@${e.targetName}"
        "channel" -> "канал «${e.targetName}»"
        else -> "группу «${e.targetName}»"
    }
    return when (e.action) {
        "ban" -> if (e.targetType == "user") "забанил $target ${untilText(e.until)}" else "заблокировал $target"
        "unban" -> if (e.targetType == "user") "разбанил $target" else "разблокировал $target"
        "restrict" -> "ограничил $target ${untilText(e.until)}"
        "unrestrict" -> "снял ограничение с $target"
        "delete" -> "удалил $target"
        "device_ban" -> "забанил по устройству и сети $target"
        "device_unban" -> "снял бан устройства или сети $target"
        "flux_grant" -> "начислил FLUX: $target"
        "flux_take" -> "списал FLUX: $target"
        "verify" -> "выдал галочку: $target"
        "unverify" -> "снял галочку: $target"
        else -> "${e.action} $target"
    }
}

@Composable
fun ModerationLog(modifier: Modifier) {
    val api = RyzikApp.instance.repo.api
    var log by remember { mutableStateOf<List<ModerationLogEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { api.moderationLog() }.onSuccess { log = it }.onFailure { error = it.userMessage() } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) } }
        if (log.isEmpty() && error == null) item {
            Text("Пока никого не наказывали.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        items(log, key = { it.id }) { e ->
            ModCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (e.action) {
                        "ban", "delete", "device_ban" -> Icons.Rounded.Block
                        "restrict" -> Icons.Rounded.SpeakerNotesOff
                        "unban", "unrestrict" -> Icons.Rounded.CheckCircle
                        "flux_grant", "flux_take" -> Icons.Rounded.Bolt
                        else -> Icons.Rounded.History
                    }
                    Icon(icon, null, tint = if (e.action.startsWith("flux")) Color(0xFFFF9F43) else if (e.action.startsWith("un") || e.action == "device_unban") Color(0xFF2FBF71) else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.admin?.displayName ?: "Админ"} ${actionText(e)}", style = MaterialTheme.typography.bodyMedium)
                        if (e.reason.isNotBlank()) Text(if (e.action.startsWith("flux")) e.reason else "Причина: ${e.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatListTime(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.targetType == "user") Icon(Icons.Rounded.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Выдача FLUX. Отрицательное число списывает. */
@Composable
private fun FluxGrantDialog(u: User, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var take by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val value = amount.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { FluxIcon(32.dp) },
        title = { Text("FLUX для @${u.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Сейчас: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FluxAmount(u.flux)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !take, onClick = { take = false }, label = { Text("Выдать") }, shape = RoundedCornerShape(14.dp))
                    FilterChip(selected = take, onClick = { take = true }, label = { Text("Забрать") }, shape = RoundedCornerShape(14.dp))
                }
                OutlinedTextField(
                    amount, { v -> amount = v.filter { it.isDigit() }.take(9) },
                    label = { Text("Сколько FLUX") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100L, 500L, 1000L, 5000L).forEach { p ->
                        AssistChip(onClick = { amount = p.toString() }, label = { Text(formatFlux(p)) }, shape = RoundedCornerShape(12.dp))
                    }
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Комментарий (необязательно)") }, shape = RoundedCornerShape(16.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = value > 0, onClick = { onConfirm(if (take) -value else value, note.trim()) }) {
                Text(if (take) "Забрать" else "Выдать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
