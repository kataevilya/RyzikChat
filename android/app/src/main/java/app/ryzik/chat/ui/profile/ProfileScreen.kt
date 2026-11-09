package app.ryzik.chat.ui.profile

import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ryzik.chat.ui.components.VerifiedMark
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.ui.flux.GiftShopSheet
import app.ryzik.chat.ui.flux.ProfileGifts
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.Badge
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.BadgeChip
import app.ryzik.chat.ui.components.NameWithBadges
import app.ryzik.chat.ui.components.formatLastSeen
import kotlinx.coroutines.launch

/**
 * Профиль пользователя. Администратор видит здесь управление бейджами.
 * Свой профиль (вкладка «Профиль») можно менять: фото, имя, «о себе», оформление.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: (() -> Unit)?,
    onOpenChat: (String) -> Unit,
    onOpenProfileLook: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
) {
    val repo = RyzikApp.instance.repo
    val users by repo.users.collectAsState()
    val contacts by repo.contacts.collectAsState()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    val isMe = userId == me?.id
    val user = if (isMe) me else users[userId]
    val scope = rememberCoroutineScope()
    var allBadges by remember { mutableStateOf<List<Badge>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var blocked by remember(userId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    val isContact = contacts.any { it.id == userId }
    var giftSheet by remember { mutableStateOf(false) }
    var giftsKey by remember { mutableIntStateOf(0) }
    var openInvite by remember { mutableStateOf<String?>(null) }
    var channelPicker by remember { mutableStateOf(false) }
    var musicBusy by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        runCatching { repo.loadUser(userId) }.onSuccess { blocked = it.isBlocked }.onFailure { error = it.userMessage() }
        if (me?.isAdmin == true) allBadges = runCatching { repo.badges() }.getOrDefault(emptyList())
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isMe) "Мой профиль" else "Профиль") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            actions = {
                if (isMe) IconButton(onClick = { name = me?.displayName.orEmpty(); bio = me?.bio.orEmpty(); editing = true }) {
                    Icon(Icons.Rounded.Edit, "Изменить")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (user == null) {
                Text(error ?: "Загрузка…", Modifier.padding(32.dp))
                return@Column
            }
            val pop = remember { Animatable(0.6f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            app.ryzik.chat.ui.premium.ProfileHeader(
                name = user.displayName,
                avatarUrl = repo.avatarUrl(user.avatarFileId),
                online = user.online && !isMe,
                isPremium = user.isPremium,
                isAdmin = user.isAdmin,
                verified = user.verified,
                emojiStatus = user.emojiStatus,
                style = user.profileStyle,
                bannerUrl = repo.avatarUrl(user.profileStyle?.bannerFileId),
                avatarScale = pop.value,
            )
            val clip = androidx.compose.ui.platform.LocalClipboardManager.current
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Text(
                "@${user.username}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    clip.setText(androidx.compose.ui.text.AnnotatedString("@${user.username}"))
                    android.widget.Toast.makeText(ctx, "Юзернейм скопирован", android.widget.Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (user.extraUsernames.isNotEmpty()) Text(
                "также " + user.extraUsernames.joinToString(", ") { "@$it" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (!isMe && !user.isService) Text(
                if (user.isBot) "бот" else formatLastSeen(user.online, user.lastSeen),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.bio.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(user.bio, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
            // Музыка и свой канал — сразу под аватаркой.
            user.music?.let { music ->
                Spacer(Modifier.height(12.dp))
                ProfileMusicRow(music, Modifier.padding(horizontal = 16.dp))
            }
            user.featuredChannel?.let { fc ->
                Spacer(Modifier.height(10.dp))
                FeaturedChannelCard(fc, Modifier.padding(horizontal = 16.dp)) {
                    if (repo.chats.value.any { it.id == fc.id && it.myRole != null }) onOpenChat(fc.id)
                    else openInvite = fc.username?.let { "@$it" } ?: fc.inviteCode
                }
            }

            if (isMe) {
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) {
                        uploading = true
                        scope.launch {
                            runCatching { repo.updateProfile(avatarFileId = repo.uploadAvatar(uri)) }.onFailure { error = it.userMessage() }
                            uploading = false
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    ProfileAction(Icons.Rounded.CameraAlt, if (uploading) "Загрузка…" else "Фото", Modifier.weight(1f)) {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
                    ProfileAction(Icons.Rounded.Edit, "Изменить", Modifier.weight(1f)) {
                        name = user.displayName; bio = user.bio; editing = true
                    }
                    ProfileAction(Icons.Rounded.Palette, "Оформление", Modifier.weight(1f), onClick = onOpenProfileLook)
                }
                Spacer(Modifier.height(12.dp))
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Избранное") },
                        supportingContent = { Text("Ваши сохранённые сообщения") },
                        leadingContent = { Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable(onClick = onOpenSaved),
                    )
                }
                val musicPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri != null) {
                        musicBusy = true
                        scope.launch {
                            runCatching { repo.setProfileMusic(uri) }.onFailure { error = it.userMessage() }
                            musicBusy = false
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Музыка в профиле") },
                        supportingContent = { Text(if (musicBusy) "Загрузка…" else user.music?.title?.ifBlank { "Трек" } ?: "Добавьте трек, его услышат в вашем профиле") },
                        leadingContent = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            if (user.music != null) IconButton(onClick = { scope.launch { runCatching { repo.setProfileMusic(null) }.onFailure { error = it.userMessage() } } }) {
                                Icon(Icons.Rounded.Close, "Убрать музыку")
                            }
                        },
                        modifier = Modifier.clickable(enabled = !musicBusy) { musicPicker.launch("audio/*") },
                    )
                    ListItem(
                        headlineContent = { Text("Канал в профиле") },
                        supportingContent = { Text(user.featuredChannel?.title ?: "Покажите свой канал под аватаркой") },
                        leadingContent = { Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            if (user.featuredChannel != null) IconButton(onClick = { scope.launch { runCatching { repo.setFeaturedChannel(null) }.onFailure { error = it.userMessage() } } }) {
                                Icon(Icons.Rounded.Close, "Убрать канал")
                            }
                        },
                        modifier = Modifier.clickable { channelPicker = true },
                    )
                }
            } else if (!user.isService) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (!blocked) ProfileAction(Icons.AutoMirrored.Rounded.Chat, "Написать", Modifier.weight(1f)) {
                        scope.launch { runCatching { repo.openDirect(user.id) }.onSuccess { onOpenChat(it.id) }.onFailure { error = it.userMessage() } }
                    }
                    if (!blocked) ProfileAction(
                        if (isContact) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                        if (isContact) "Убрать из контактов" else "В контакты",
                        Modifier.weight(1f),
                    ) {
                        scope.launch { runCatching { repo.setContact(user.id, !isContact) }.onFailure { error = it.userMessage() } }
                    }
                    ProfileAction(
                        Icons.Rounded.Block,
                        if (blocked) "Разблокировать" else "Заблокировать",
                        Modifier.weight(1f),
                        danger = !blocked,
                    ) {
                        scope.launch {
                            runCatching { repo.setBlocked(user.id, !blocked) }
                                .onSuccess { blocked = !blocked }
                                .onFailure { error = it.userMessage() }
                        }
                    }
                }
                if (!blocked) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        ProfileAction(Icons.Rounded.CardGiftcard, "Подарить подарок", Modifier.weight(1f)) { giftSheet = true }
                    }
                }
                if (user.messagePrice > 0 && !isContact) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Сообщения этому человеку стоят ${user.messagePrice} FLUX, пока вы не в его контактах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            if (user.badges.isNotEmpty() || user.isAdmin) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    if (user.isAdmin) BadgeChip(Badge("admin", "", "Администратор", color = "#6750A4"))
                    user.badges.forEach { BadgeChip(it) }
                }
            }

            Spacer(Modifier.height(20.dp))
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ListItem(
                    headlineContent = { Text("Ключ шифрования") },
                    supportingContent = {
                        Text(repo.fingerprintOf(user), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = { Icon(Icons.Rounded.Lock, null) },
                )
                Text(
                    "Сверьте эти цифры с собеседником при встрече: если совпадают, никто не подменил ключи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }

            if (me?.isAdmin == true) {
                Spacer(Modifier.height(16.dp))
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Администрирование", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Бейджи пользователя", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        if (allBadges.isEmpty()) {
                            Text(
                                "Бейджей пока нет. Создайте их в «Настройки → Админ-панель».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allBadges.forEach { b ->
                                val has = user.badges.any { it.id == b.id }
                                FilterChip(
                                    selected = has,
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                if (has) repo.revokeBadge(user.id, b.id) else repo.grantBadge(user.id, b.id)
                                            }.onFailure { error = it.userMessage() }
                                        }
                                    },
                                    label = { Text("${b.emoji} ${b.title}") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerifiedMark(24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Галочка верификации", Modifier.weight(1f))
                            Switch(checked = user.verified, onCheckedChange = { v ->
                                scope.launch { runCatching { repo.setVerified(user.id, v) }.onFailure { error = it.userMessage() } }
                            })
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Премиум", Modifier.weight(1f))
                            Switch(checked = user.isPremium, onCheckedChange = { v ->
                                scope.launch { runCatching { repo.setPremium(user.id, v) }.onFailure { error = it.userMessage() } }
                            })
                        }
                        if (user.id != me?.id) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shield, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Администратор", Modifier.weight(1f))
                                Switch(checked = user.isAdmin, onCheckedChange = { v ->
                                    scope.launch { runCatching { repo.setAdmin(user.id, v) }.onFailure { error = it.userMessage() } }
                                })
                            }
                        }
                    }
                }
            }
            // Подарки — в самом конце профиля.
            if (!user.isService) {
                Spacer(Modifier.height(8.dp))
                ProfileGifts(user.id, isMe, giftsKey)
            }
            if (giftSheet) GiftShopSheet(user, onDismiss = { giftSheet = false }, onGifted = { giftSheet = false; giftsKey++ })
            AnimatedVisibility(error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    openInvite?.let { code ->
        app.ryzik.chat.ui.channel.InviteDialog(code, onDismiss = { openInvite = null }, onOpenChat = { id -> openInvite = null; onOpenChat(id) })
    }
    if (channelPicker) {
        val chats by repo.chats.collectAsState()
        val mine = chats.filter { it.type == "channel" && !it.isService && (it.myRole == "owner" || it.myRole == "admin") }
        AlertDialog(
            onDismissRequest = { channelPicker = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Канал в профиле") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (mine.isEmpty()) Text("Здесь появятся каналы, где вы владелец или админ.")
                    mine.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (c.myRole == "owner") "вы владелец" else "вы админ") },
                            leadingContent = { Avatar(c.title, repo.avatarUrl(c.avatarFileId), 40.dp) },
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                channelPicker = false
                                scope.launch { runCatching { repo.setFeaturedChannel(c.id) }.onFailure { error = it.userMessage() } }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { channelPicker = false }) { Text("Закрыть") } },
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Профиль") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(64) }, label = { Text("Имя") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(bio, { bio = it.take(300) }, label = { Text("О себе") }, maxLines = 4)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    editing = false
                    scope.launch { runCatching { repo.updateProfile(displayName = name.trim(), bio = bio.trim()) }.onFailure { error = it.userMessage() } }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } },
        )
    }
}

/** Поиск бота по имени, чтобы добавить его в группу или канал. */
@Composable
private fun AddBotDialog(onDismiss: () -> Unit, onPick: (app.ryzik.chat.data.User) -> Unit) {
    val repo = RyzikApp.instance.repo
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<app.ryzik.chat.data.User>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.length < 2) { found = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        found = runCatching { repo.searchUsers(query) }.getOrDefault(emptyList()).filter { it.isBot && it.id != "ryzikbot" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Добавить бота") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("@юзернейм бота") }, shape = RoundedCornerShape(16.dp))
                if (query.length >= 2 && found.isEmpty()) Text("Ботов не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                found.take(6).forEach { b ->
                    ListItem(
                        headlineContent = { Text(b.displayName, maxLines = 1) },
                        supportingContent = { Text("@${b.username}") },
                        leadingContent = { Avatar(b.displayName, repo.avatarUrl(b.avatarFileId), 36.dp) },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(b) },
                    )
                }
                Text("Своего бота можно создать в чате с @ryzikbot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Музыка в профиле: кнопка воспроизведения и название трека. */
@Composable
private fun ProfileMusicRow(music: app.ryzik.chat.data.ProfileMusic, modifier: Modifier = Modifier) {
    val repo = RyzikApp.instance.repo
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val id = "music:${music.fileId}"
    val current by app.ryzik.chat.ui.chat.InlinePlayer.current.collectAsState()
    val playing by app.ryzik.chat.ui.chat.InlinePlayer.playing.collectAsState()
    val progress by app.ryzik.chat.ui.chat.InlinePlayer.progress.collectAsState()
    var loading by remember(music.fileId) { mutableStateOf(false) }
    val mine = current == id
    DisposableEffect(id) { onDispose { if (app.ryzik.chat.ui.chat.InlinePlayer.current.value == id) app.ryzik.chat.ui.chat.InlinePlayer.stop() } }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = {
                if (loading) return@FilledIconButton
                scope.launch {
                    loading = true
                    runCatching { repo.profileMusicFile(music.fileId) }.onSuccess { f -> app.ryzik.chat.ui.chat.InlinePlayer.toggle(context, id, f) }
                    loading = false
                }
            }) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(if (mine && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (mine && playing) "Пауза" else "Слушать")
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(music.title.ifBlank { "Музыка" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { if (mine) progress else 0f }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Канал, который человек показывает в профиле. */
@Composable
private fun FeaturedChannelCard(fc: app.ryzik.chat.data.FeaturedChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val repo = RyzikApp.instance.repo
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(fc.title, repo.avatarUrl(fc.avatarFileId), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (fc.verified) { Spacer(Modifier.width(4.dp)); VerifiedMark(16.dp) }
                }
                Text(
                    listOfNotNull("канал", fc.username?.let { "@$it" }, app.ryzik.chat.ui.channel.membersText(fc.memberCount).takeIf { fc.memberCount > 0 }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Кнопка-плитка под шапкой профиля. */
@Composable
private fun ProfileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Информация о чате: для личного — профиль собеседника, для группы — участники. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatInfoScreen(
    chatId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onAddMembers: () -> Unit,
    onLeft: () -> Unit,
) {
    val repo = RyzikApp.instance.repo
    val chats by repo.chats.collectAsState()
    val users by repo.users.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var addingBot by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var avatarBusy by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var editUsername by remember { mutableStateOf<String?>(null) }
    var discussionDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val myId = repo.myId
    val auth by repo.auth.collectAsState()
    val isAdmin = (auth as? AuthState.LoggedIn)?.me?.isAdmin == true

    if (chat?.type == "direct") {
        val peer = repo.peerOf(chat)
        if (peer != null) {
            ProfileScreen(peer.id, onBack, onOpenChat)
            return
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(when (chat?.type) { "saved" -> "Избранное"; "channel" -> "Канал"; else -> "Группа" }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            actions = {
                val owner = chat?.members?.any { it.user.id == myId && it.role == "owner" } == true
                if ((chat?.type == "group" && owner) || (chat?.type == "channel" && (chat.myRole == "owner" || chat.myRole == "admin"))) {
                    IconButton(onClick = { newTitle = chat.title; newDescription = chat.description; renaming = true }) { Icon(Icons.Rounded.Edit, "Изменить") }
                }
            },
        )
    }) { padding ->
        if (chat == null) return@Scaffold
        val owner = chat.members.any { it.user.id == myId && it.role == "owner" } || chat.myRole == "owner"
        val canEdit = (chat.type == "group" && owner) || (chat.type == "channel" && (chat.myRole == "owner" || chat.myRole == "admin"))
        val isGroupOrChannel = chat.type == "group" || chat.type == "channel"
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (canEdit) {
                        app.ryzik.chat.ui.channel.AvatarPicker(repo.chatTitle(chat), repo.avatarUrl(chat.avatarFileId), 110.dp, busy = avatarBusy) { uri ->
                            avatarBusy = true
                            scope.launch {
                                runCatching { repo.setChatAvatar(chat.id, repo.uploadAvatar(uri)) }
                                    .onFailure { infoError = it.userMessage() }
                                avatarBusy = false
                            }
                        }
                    } else Avatar(repo.chatTitle(chat), repo.avatarUrl(chat.avatarFileId), 110.dp, saved = chat.type == "saved", service = chat.isService)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(repo.chatTitle(chat), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                        if (chat.verified && chat.type != "direct") { Spacer(Modifier.width(6.dp)); VerifiedMark(24.dp) }
                    }
                    if (chat.isService) {
                        Text("официальный чат RyzikChat", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Сюда приходят коды для входа в ваш аккаунт с других устройств и важные новости. Никому не сообщайте эти коды.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    } else if (isGroupOrChannel) {
                        val count = if (chat.type == "group") app.ryzik.chat.ui.channel.membersText(maxOf(chat.memberCount, chat.members.size))
                        else app.ryzik.chat.ui.chats.subscribersText(chat.memberCount)
                        val kind = when {
                            chat.type == "group" && chat.isPublic -> "публичная группа"
                            chat.type == "group" -> "частная группа"
                            chat.isPublic -> "публичный канал"
                            else -> "частный канал"
                        }
                        Text("$kind · $count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        chat.username?.let { Text("@$it", color = MaterialTheme.colorScheme.primary) }
                        if (chat.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(chat.description, textAlign = TextAlign.Center)
                        }
                    }
                    if (isAdmin && isGroupOrChannel && !chat.isService) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            VerifiedMark(24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Галочка верификации", Modifier.weight(1f))
                            Switch(checked = chat.verified, onCheckedChange = { v ->
                                scope.launch { runCatching { repo.setChatVerified(chat.id, v) }.onFailure { infoError = it.userMessage() } }
                            })
                        }
                    }
                    infoError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                    if (chat.type == "saved") Text(
                        "Здесь хранятся ваши заметки и сохранённые сообщения. Они тоже зашифрованы.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            chat.username?.takeIf { isGroupOrChannel }?.let { uname ->
                item {
                    val link = app.ryzik.chat.ui.channel.usernameLink(uname)
                    ListItem(
                        headlineContent = { Text("@$uname") },
                        supportingContent = { Text("$link · нажмите, чтобы скопировать") },
                        leadingContent = { Icon(Icons.Rounded.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            IconButton(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, "«${chat.title}» в RyzikChat: @$uname ($link)")
                                context.startActivity(android.content.Intent.createChooser(send, "Поделиться"))
                            }) { Icon(Icons.Rounded.Share, "Поделиться") }
                        },
                        modifier = Modifier.clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString("@$uname"))
                            android.widget.Toast.makeText(context, "Юзернейм скопирован", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            if (isGroupOrChannel && chat.myRole == "owner" && chat.isPublic) item {
                ListItem(
                    headlineContent = { Text(if (chat.username == null) "Задать юзернейм" else "Изменить юзернейм") },
                    supportingContent = { Text("Публичная ссылка вида @имя, по ней находят в поиске") },
                    leadingContent = { Icon(Icons.Rounded.Edit, null) },
                    modifier = Modifier.clickable { editUsername = chat.username.orEmpty() },
                )
            }
            if (isGroupOrChannel && chat.myRole != null) {
                val code = chat.inviteCode
                if (code != null) item {
                    val link = app.ryzik.chat.ui.channel.inviteLink(code)
                    ListItem(
                        headlineContent = { Text(link, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        supportingContent = { Text("Ссылка-приглашение · нажмите, чтобы скопировать") },
                        leadingContent = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            IconButton(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, "Присоединяйся к «${chat.title}» в RyzikChat: $link")
                                context.startActivity(android.content.Intent.createChooser(send, "Поделиться ссылкой"))
                            }) { Icon(Icons.Rounded.Share, "Поделиться") }
                        },
                        modifier = Modifier.clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
                            android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (canEdit && code != null) item {
                    ListItem(
                        headlineContent = { Text("Сменить ссылку") },
                        supportingContent = { Text("Старая ссылка перестанет работать") },
                        leadingContent = { Icon(Icons.Rounded.Refresh, null) },
                        modifier = Modifier.clickable { scope.launch { runCatching { repo.resetInvite(chat.id) }.onFailure { infoError = it.userMessage() } } },
                    )
                }
                if (chat.myRole == "owner") item {
                    ListItem(
                        headlineContent = { Text(if (chat.type == "channel") "Публичный канал" else "Публичная группа") },
                        supportingContent = {
                            Text(
                                if (chat.isPublic) "Виден в поиске, ${if (chat.type == "channel") "подписаться" else "вступить"} может любой"
                                else "Скрыт из поиска, только по ссылке-приглашению"
                            )
                        },
                        leadingContent = { Icon(if (chat.isPublic) Icons.Rounded.Public else Icons.Rounded.Lock, null) },
                        trailingContent = {
                            androidx.compose.material3.Switch(chat.isPublic, onCheckedChange = { v ->
                                scope.launch { runCatching { repo.setChatPublic(chat.id, v) }.onFailure { infoError = it.userMessage() } }
                            })
                        },
                    )
                }
            }
            if (chat.type == "channel" && chat.myRole == "owner") item {
                val group = chat.discussionId?.let { id -> chats.firstOrNull { it.id == id } }
                ListItem(
                    headlineContent = { Text("Комментарии") },
                    supportingContent = {
                        Text(if (chat.discussionId == null) "Выключены. Привяжите группу, и в ней будут комментарии к постам" else "Обсуждение в группе «${group?.title ?: "группа"}»")
                    },
                    leadingContent = { Icon(Icons.Rounded.Forum, null) },
                    modifier = Modifier.clickable { discussionDialog = true },
                )
            }
            if (app.ryzik.chat.ui.chats.canDelete(chat)) item {
                ListItem(
                    headlineContent = { Text(app.ryzik.chat.ui.chats.deleteTitle(chat), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { deleteDialog = true },
                )
            }
            chat.linkedChannelId?.let { channelId ->
                if (chat.type == "group") item {
                    ListItem(
                        headlineContent = { Text("Обсуждение канала") },
                        supportingContent = { Text(chats.firstOrNull { it.id == channelId }?.title ?: "Сюда приходят комментарии к постам канала") },
                        leadingContent = { Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { if (chats.any { it.id == channelId }) onOpenChat(channelId) },
                    )
                }
            }
            if (chat.type == "group" && chat.myRole == null && chat.isPublic) item {
                ListItem(
                    headlineContent = { Text("Вступить в группу", color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { scope.launch { runCatching { repo.subscribe(chat.id) }.onFailure { infoError = it.userMessage() } } },
                )
            }
            if (chat.type == "channel" && !chat.isService) {
                if (chat.myRole == "owner" || chat.myRole == "admin") item {
                    ListItem(
                        headlineContent = { Text("Добавить бота") },
                        supportingContent = { Text("Бот станет админом и сможет публиковать посты") },
                        leadingContent = { Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { addingBot = true },
                    )
                }
                if (chat.myRole != null) item {
                    ListItem(
                        headlineContent = { Text(if (chat.muted) "Включить звук" else "Выключить звук") },
                        leadingContent = { Icon(if (chat.muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null) },
                        modifier = Modifier.clickable { scope.launch { runCatching { repo.setMuted(chat.id, !chat.muted) } } },
                    )
                }
                item {
                    Text("Администраторы", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                }
                items(chat.members.size, key = { chat.members[it].user.id }) { i ->
                    val m = chat.members[i]
                    val u = users[m.user.id] ?: m.user
                    ListItem(
                        headlineContent = { NameWithBadges(u.displayName, u.badges, u.isAdmin, MaterialTheme.typography.bodyLarge, isPremium = u.isPremium, emojiStatus = u.emojiStatus, verified = u.verified) },
                        supportingContent = { Text(if (m.role == "owner") "владелец" else "админ") },
                        leadingContent = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 44.dp, online = u.online) },
                        trailingContent = {
                            if (chat.myRole == "owner" && m.role == "admin") IconButton(onClick = {
                                scope.launch { runCatching { repo.setChannelAdmin(chat.id, u.id, false) } }
                            }) { Icon(Icons.Rounded.PersonRemove, "Снять админа") }
                        },
                        modifier = Modifier.clickable { onOpenProfile(u.id) },
                    )
                }
                item {
                    if (chat.myRole == null) {
                        ListItem(
                            headlineContent = { Text("Подписаться", color = MaterialTheme.colorScheme.primary) },
                            leadingContent = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { scope.launch { runCatching { repo.subscribe(chat.id) } } },
                        )
                    } else if (chat.myRole != "owner") {
                        ListItem(
                            headlineContent = { Text("Отписаться", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { scope.launch { runCatching { repo.leave(chat.id) }.onSuccess { onLeft() } } },
                        )
                    }
                }
            }
            if (chat.type == "group" && chat.myRole != null) {
                if (owner) item {
                    ListItem(
                        headlineContent = { Text("Добавить участников") },
                        leadingContent = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable(onClick = onAddMembers),
                    )
                }
                if (chat.myRole == "owner" || chat.myRole == "admin") item {
                    ListItem(
                        headlineContent = { Text("Добавить бота") },
                        supportingContent = { Text("Сообщения в группе с ботом не шифруются сквозным шифрованием") },
                        leadingContent = { Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { addingBot = true },
                    )
                }
                items(chat.members.size, key = { chat.members[it].user.id }) { i ->
                    val m = chat.members[i]
                    val u = users[m.user.id] ?: m.user
                    ListItem(
                        headlineContent = { NameWithBadges(u.displayName, u.badges, u.isAdmin, MaterialTheme.typography.bodyLarge, isPremium = u.isPremium, emojiStatus = u.emojiStatus, verified = u.verified) },
                        supportingContent = {
                            Text(
                                when {
                                    m.role == "owner" -> "владелец"
                                    m.role == "admin" -> "админ"
                                    u.isBot -> "бот"
                                    else -> formatLastSeen(u.online, u.lastSeen)
                                },
                            )
                        },
                        leadingContent = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 44.dp, online = u.online) },
                        trailingContent = {
                            if (owner && u.id != myId) Row {
                                // Админка только в этой группе: права в приложении не меняются.
                                IconButton(onClick = {
                                    scope.launch { runCatching { repo.setChannelAdmin(chat.id, u.id, m.role != "admin") }.onFailure { infoError = it.userMessage() } }
                                }) {
                                    Icon(
                                        if (m.role == "admin") Icons.Rounded.RemoveModerator else Icons.Rounded.AddModerator,
                                        if (m.role == "admin") "Снять админа" else "Сделать админом",
                                        tint = if (m.role == "admin") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch { runCatching { repo.removeMember(chat.id, u.id) } }
                                }) { Icon(Icons.Rounded.PersonRemove, "Исключить") }
                            }
                        },
                        modifier = Modifier.animateItem().clickable { onOpenProfile(u.id) },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Покинуть группу", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            scope.launch { runCatching { repo.removeMember(chat.id, myId!!) }.onSuccess { onLeft() } }
                        },
                    )
                }
            }
        }
    }

    if (addingBot && chat != null) {
        AddBotDialog(onDismiss = { addingBot = false }) { bot ->
            addingBot = false
            scope.launch { runCatching { repo.addBot(chat.id, bot.id) }.onFailure { infoError = it.userMessage() } }
        }
    }
    if (renaming && chat != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(if (chat.type == "channel") "Канал" else "Группа") },
            text = {
                Column {
                    OutlinedTextField(newTitle, { newTitle = it.take(128) }, singleLine = true, label = { Text("Название") })
                    if (chat.type == "channel" || chat.type == "group") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(newDescription, { newDescription = it.take(500) }, label = { Text("Описание") }, maxLines = 5)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        runCatching {
                            repo.updateChannel(chat.id, newTitle.trim(), newDescription.trim())
                        }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена") } },
        )
    }
    val usernameDraft = editUsername
    if (usernameDraft != null && chat != null) {
        var value by remember(chat.id) { mutableStateOf(usernameDraft) }
        var ok by remember { mutableStateOf(true) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editUsername = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Публичная ссылка") },
            text = {
                Column {
                    app.ryzik.chat.ui.channel.ChatUsernameField(value, { value = it }, chatId = chat.id, onValid = { ok = it })
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = ok || value.isBlank(), onClick = {
                    scope.launch {
                        runCatching { repo.setChatUsername(chat.id, value.trim()) }
                            .onSuccess { editUsername = null }
                            .onFailure { err = it.userMessage() }
                    }
                }) { Text(if (value.isBlank()) "Убрать" else "Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editUsername = null }) { Text("Отмена") } },
        )
    }

    val dChat = chat
    if (deleteDialog && dChat != null) {
        app.ryzik.chat.ui.chats.DeleteChatDialog(dChat, onDismiss = { deleteDialog = false }) { deleteDialog = false; onLeft() }
    }
    if (discussionDialog && dChat != null) {
        val myGroups = chats.filter { it.type == "group" && (it.myRole == "owner" || it.myRole == "admin") }
        var busy by remember { mutableStateOf(false) }
        fun apply(block: suspend () -> Unit) {
            busy = true
            scope.launch {
                runCatching { block() }.onSuccess { discussionDialog = false }.onFailure { infoError = it.userMessage() }
                busy = false
            }
        }
        AlertDialog(
            onDismissRequest = { discussionDialog = false },
            icon = { Icon(Icons.Rounded.Forum, null) },
            title = { Text("Комментарии к постам") },
            text = {
                LazyColumn {
                    item {
                        Text(
                            "Комментарии хранятся в привязанной группе: участники видят их там, а подписчики — под каждым постом.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Создать новую группу", color = MaterialTheme.colorScheme.primary) },
                            leadingContent = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable(enabled = !busy) { apply { repo.createDiscussion(dChat) } },
                        )
                    }
                    items(myGroups.size) { i ->
                        val g = myGroups[i]
                        ListItem(
                            headlineContent = { Text(g.title) },
                            supportingContent = { if (g.id == dChat.discussionId) Text("Привязана сейчас", color = MaterialTheme.colorScheme.primary) },
                            leadingContent = { Avatar(g.title, repo.avatarUrl(g.avatarFileId), 36.dp) },
                            modifier = Modifier.clickable(enabled = !busy && g.id != dChat.discussionId) { apply { repo.setDiscussion(dChat.id, g.id) } },
                        )
                    }
                }
            },
            confirmButton = {
                if (dChat.discussionId != null) TextButton(enabled = !busy, onClick = { apply { repo.setDiscussion(dChat.id, null) } }) {
                    Text("Выключить комментарии", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { discussionDialog = false }) { Text("Закрыть") } },
        )
    }
}
