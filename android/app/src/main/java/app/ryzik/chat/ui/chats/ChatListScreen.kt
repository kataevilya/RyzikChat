package app.ryzik.chat.ui.chats

import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import app.ryzik.chat.ui.components.EmptyIcon
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.statusBarsPadding
import app.ryzik.chat.ui.components.AvatarShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.ui.channel.InviteDialog
import app.ryzik.chat.ui.channel.membersText
import app.ryzik.chat.ui.channel.parseInviteCode
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.Chat
import app.ryzik.chat.data.userMessage
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import app.ryzik.chat.data.User
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.VerifiedMark
import app.ryzik.chat.ui.components.BadgeIcons
import app.ryzik.chat.ui.components.TypingDots
import app.ryzik.chat.ui.components.formatListTime
import app.ryzik.chat.ui.stories.StoriesBar
import app.ryzik.chat.ui.stories.StoryComposerDialog
import app.ryzik.chat.ui.stories.StoryViewerDialog
import app.ryzik.chat.ui.stories.StreakBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Свайп чата влево — в архив (или обратно из архива). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveSwipe(enabled: Boolean, archived: Boolean, onSwiped: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    if (!enabled) { Box(modifier) { content() }; return }
    val currentOnSwiped by androidx.compose.runtime.rememberUpdatedState(onSwiped)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) currentOnSwiped()
            false // строка возвращается на место, а чат уезжает в архив сам
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val bg by androidx.compose.animation.animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, label = "swipe",
            )
            val iconScale by animateFloatAsState(if (active) 1.2f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "swipeIcon")
            Row(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null, Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = tint)
                    Text(if (archived) "Вернуть" else "В архив", style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        },
    ) { content() }
}

private enum class Filter(val title: String) { All("Все"), Unread("Непрочитанные"), Personal("Личные"), Groups("Группы"), Channels("Каналы") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val app = RyzikApp.instance
    val repo = app.repo
    val chats by repo.chats.collectAsState()
    val users by repo.users.collectAsState()
    val typing by repo.typing.collectAsState()
    val connected by repo.connected.collectAsState()
    val loading by repo.chatsLoading.collectAsState()
    val auth by repo.auth.collectAsState()
    val settings by app.prefs.settings.collectAsState(initial = null)
    val me = (auth as? AuthState.LoggedIn)?.me
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var foundUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var foundChannels by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var fabOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(Filter.All) }
    var showArchive by remember { mutableStateOf(false) }
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    var openInvite by remember { mutableStateOf<String?>(null) }
    var storyGroup by remember { mutableStateOf<Int?>(null) }
    var composeStory by remember { mutableStateOf(false) }
    val storyGroups by repo.stories.collectAsState()
    LaunchedEffect(Unit) { repo.refreshStories() }
    storyGroup?.let { i -> StoryViewerDialog(storyGroups, i) { storyGroup = null } }
    if (composeStory) StoryComposerDialog { composeStory = false }
    openInvite?.let { code ->
        InviteDialog(code, onDismiss = { openInvite = null }, onOpenChat = { id -> openInvite = null; onOpenChat(id) })
    }

    LaunchedEffect(query) {
        if (query.length < 2) { foundUsers = emptyList(); return@LaunchedEffect }
        delay(300)
        foundUsers = runCatching { repo.searchUsers(query) }.getOrDefault(emptyList())
        foundChannels = runCatching { repo.searchChannels(query) }.getOrDefault(emptyList()).filter { c -> chats.none { it.id == c.id && it.myRole != null } }
    }

    val listState = rememberLazyListState()

    val archivedCount = chats.count { it.archived }
    val visible = chats.filter { c ->
        val title = repo.chatTitle(c)
        !((c.type == "channel" || c.type == "group") && c.myRole == null) &&
        // Личный чат появляется в списке только после первого сообщения.
        !(c.type == "direct" && c.lastMessage == null) &&
        (if (showArchive) c.archived else !c.archived) &&
            (query.isBlank() || title.contains(query, ignoreCase = true)) &&
            when (filter) {
                Filter.All -> true
                Filter.Unread -> c.unread > 0
                Filter.Personal -> c.type == "direct" || c.type == "saved"
                Filter.Groups -> c.type == "group"
                Filter.Channels -> c.type == "channel"
            }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(searching || showArchive, label = "lead") { back ->
                        if (back) RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") { searching = false; query = ""; showArchive = false }
                        else Box {
                            RoundButton(Icons.Rounded.Add, "Создать", primary = true) { fabOpen = true }
                            DropdownMenu(fabOpen, onDismissRequest = { fabOpen = false }, shape = RoundedCornerShape(20.dp)) {
                                DropdownMenuItem(text = { Text("Новый чат") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, onClick = { fabOpen = false; onNewChat() })
                                DropdownMenuItem(text = { Text("Новая группа") }, leadingIcon = { Icon(Icons.Rounded.Group, null) }, onClick = { fabOpen = false; onNewGroup() })
                                DropdownMenuItem(text = { Text("Новый канал") }, leadingIcon = { Icon(Icons.Rounded.Campaign, null) }, onClick = { fabOpen = false; onNewChannel() })
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    SearchPill(
                        query = query,
                        onQuery = { query = it; if (it.isNotEmpty()) searching = true },
                        placeholder = if (showArchive) "Поиск в архиве" else "Поиск",
                        onFocus = { if (it) searching = true },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.clip(AvatarShape).clickable(onClick = onOpenSettings)) {
                        Avatar(me?.displayName ?: "?", repo.avatarUrl(me?.avatarFileId), 44.dp)
                    }
                }
                AnimatedVisibility(!connected || showArchive) {
                    Row(Modifier.padding(start = 20.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showArchive) "Архив" else "Соединение",
                            style = if (showArchive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
                            color = if (showArchive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!connected) {
                            Spacer(Modifier.width(4.dp))
                            TypingDots(MaterialTheme.colorScheme.onSurfaceVariant, 3.dp)
                        }
                    }
                }
                AnimatedVisibility(!searching && !showArchive) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        items(Filter.entries) { f ->
                            val sel = filter == f
                            val bg by androidx.compose.animation.animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chip")
                            val fg by androidx.compose.animation.animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "chipFg")
                            Text(
                                f.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = fg,
                                modifier = Modifier.clip(CircleShape).background(bg).clickable { filter = f }.padding(horizontal = 16.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                AnimatedVisibility(loading && chats.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(CircleShape)) }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { scope.launch { repo.refreshChats() } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                if (!searching && !showArchive) {
                    item(key = "stories") {
                        StoriesBar(
                            me = me,
                            onOpen = { storyGroup = it },
                            onAdd = { composeStory = true },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                if (!searching && !showArchive && archivedCount > 0) {
                    item(key = "archive") {
                        Row(
                            Modifier.animateItem().padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                                .combinedClickable(onClick = { showArchive = true }).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp).clip(AvatarShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Archive, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Архив", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                archivedCount.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                items(visible, key = { it.id }) { chat ->
                    ArchiveSwipe(
                        enabled = chat.type != "saved" && !chat.isService,
                        archived = chat.archived,
                        onSwiped = { scope.launch { runCatching { repo.setArchived(chat.id, !chat.archived) } } },
                        modifier = Modifier.animateItem().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp)),
                    ) {
                    ChatRow(
                        chat = chat,
                        title = repo.chatTitle(chat),
                        peer = repo.peerOf(chat)?.let { users[it.id] ?: it },
                        preview = repo.previewOf(chat.lastMessage),
                        typingNames = typing[chat.id].orEmpty().keys.mapNotNull { users[it]?.displayName },
                        myId = me?.id,
                        compact = settings?.compactList == true,
                        avatarUrl = repo.avatarUrl(if (chat.type == "direct") repo.peerOf(chat)?.avatarFileId else chat.avatarFileId),
                        modifier = Modifier
                            .background(if (chat.pinned) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
                            .combinedClickable(onClick = { onOpenChat(chat.id) }, onLongClick = { menuChat = chat }),
                    )
                    }
                }
                if (searching && foundUsers.isNotEmpty()) {
                    item(key = "people") {
                        Text(
                            "Люди",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(foundUsers, key = { "u_" + it.id }) { u ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BadgeIcons(u.badges, u.isAdmin, isPremium = u.isPremium, emojiStatus = u.emojiStatus, verified = u.verified)
                                }
                            },
                            supportingContent = { Text("@${u.username}") },
                            leadingContent = { Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 48.dp, online = u.online) },
                            modifier = Modifier.animateItem().combinedClickable(
                                onClick = {
                                    scope.launch {
                                        runCatching { repo.openDirect(u.id) }.onSuccess { onOpenChat(it.id) }
                                    }
                                },
                                onLongClick = { onOpenProfile(u.id) },
                            ),
                        )
                    }
                }
                val inviteCode = if (query.contains("join/") || query.contains("://c/")) parseInviteCode(query) else null
                if (inviteCode != null) {
                    item(key = "invite") {
                        ListItem(
                            headlineContent = { Text("Открыть приглашение") },
                            supportingContent = { Text("Вступить в группу или канал по ссылке") },
                            leadingContent = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { openInvite = inviteCode },
                        )
                    }
                }
                if (searching && foundChannels.isNotEmpty()) {
                    item(key = "channels") {
                        Text(
                            "Каналы и группы",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(foundChannels, key = { "c_" + it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text((c.username?.let { "@$it · " } ?: "") + (if (c.type == "group") membersText(c.memberCount) else subscribersText(c.memberCount)) + c.description.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Avatar(c.title, repo.avatarUrl(c.avatarFileId), 48.dp) },
                            modifier = Modifier.animateItem().combinedClickable(onClick = { onOpenChat(c.id) }),
                        )
                    }
                }
                if (visible.isEmpty() && !loading && !(searching && (foundUsers.isNotEmpty() || foundChannels.isNotEmpty()))) {
                    item(key = "empty") { EmptyState(searching, Modifier.animateItem()) }
                }
            }
        }
    }

    var deleting by remember { mutableStateOf<Chat?>(null) }
    menuChat?.let { chat ->
        ModalBottomSheet(onDismissRequest = { menuChat = null }) {
            Text(
                repo.chatTitle(chat),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            fun act(block: suspend () -> Unit) {
                menuChat = null
                scope.launch { runCatching { block() } }
            }
            ListItem(
                headlineContent = { Text(if (chat.pinned) "Открепить" else "Закрепить") },
                leadingContent = { Icon(Icons.Rounded.PushPin, null) },
                modifier = Modifier.combinedClickable(onClick = { act { repo.setPinned(chat.id, !chat.pinned) } }),
            )
            ListItem(
                headlineContent = { Text(if (chat.muted) "Включить уведомления" else "Без звука") },
                leadingContent = { Icon(if (chat.muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null) },
                modifier = Modifier.combinedClickable(onClick = { act { repo.setMuted(chat.id, !chat.muted) } }),
            )
            if (chat.type != "saved" && !chat.isService) {
                ListItem(
                    headlineContent = { Text(if (chat.archived) "Вернуть из архива" else "В архив") },
                    leadingContent = { Icon(if (chat.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                    modifier = Modifier.combinedClickable(onClick = { act { repo.setArchived(chat.id, !chat.archived) } }),
                )
            }
            if (chat.type == "channel" && chat.myRole != "owner" && !chat.isService) {
                ListItem(
                    headlineContent = { Text("Отписаться", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = { act { repo.leave(chat.id) } }),
                )
            }
            if (chat.type == "group") {
                ListItem(
                    headlineContent = { Text("Покинуть группу", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = { act { repo.removeMember(chat.id, me!!.id) } }),
                )
            }
            if (canDelete(chat)) {
                ListItem(
                    headlineContent = { Text(deleteTitle(chat), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = { menuChat = null; deleting = chat }),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    deleting?.let { chat ->
        DeleteChatDialog(chat, onDismiss = { deleting = null }) { deleting = null }
    }
}

/** Что можно удалить: личный чат — любой участник, группу и канал — владелец. */
fun canDelete(chat: Chat): Boolean = !chat.isService && chat.type != "saved" &&
    (chat.type == "direct" || (chat.type in listOf("group", "channel") && chat.myRole == "owner"))

fun deleteTitle(chat: Chat): String = when (chat.type) {
    "group" -> "Удалить группу"
    "channel" -> "Удалить канал"
    else -> "Удалить чат"
}

/** Подтверждение удаления чата. */
@Composable
fun DeleteChatDialog(chat: Chat, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    // Личный чат: по умолчанию удаляется только у себя, галочкой — и у собеседника.
    var forAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(deleteTitle(chat) + "?") },
        text = {
            Column {
                Text(
                    when (chat.type) {
                        "direct" -> if (forAll) "Переписка с «${repo.chatTitle(chat)}» удалится у вас обоих. Это нельзя отменить."
                            else "Переписка удалится только у вас, у «${repo.chatTitle(chat)}» она останется."
                        "group" -> "Группа и все сообщения удалятся у всех участников. Это нельзя отменить."
                        else -> "Канал и все посты удалятся у всех подписчиков. Это нельзя отменить."
                    }
                )
                if (chat.type == "direct") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { forAll = !forAll },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                        Text("Удалить также у «${repo.chatTitle(chat)}»")
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { repo.deleteChat(chat.id, forAll = chat.type != "direct" || forAll) }.onSuccess { onDeleted() }.onFailure { err = it.userMessage() }
                    busy = false
                }
            }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ChatRow(
    chat: Chat,
    title: String,
    peer: User?,
    preview: String,
    typingNames: List<String>,
    myId: String?,
    compact: Boolean,
    avatarUrl: String?,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val avatarSize = if (compact) 44.dp else 54.dp
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, avatarUrl, avatarSize, online = peer?.online == true, saved = chat.type == "saved", service = chat.isService)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (chat.verified && peer == null) VerifiedMark(16.dp, Modifier.padding(start = 4.dp))
                    else if (chat.type == "channel") Icon(Icons.Rounded.Campaign, null, Modifier.padding(start = 4.dp).size(16.dp), tint = scheme.primary)
                    if (peer != null) BadgeIcons(peer.badges, peer.isAdmin, 16.dp, peer.isPremium, peer.emojiStatus, peer.verified)
                    if (chat.muted) Icon(Icons.Rounded.NotificationsOff, null, Modifier.padding(start = 4.dp).size(14.dp), tint = scheme.outline)
                    StreakBadge(chat.streak, Modifier.padding(start = 6.dp))
                }
                val last = chat.lastMessage
                if (last != null && last.senderId == myId && chat.type != "saved" && chat.type != "channel") {
                    // Одна галочка — доставлено, две — собеседник прочитал.
                    val read = (RyzikApp.instance.repo.readStates.collectAsState().value[chat.id].orEmpty().filterKeys { it != myId }.values.maxOrNull() ?: 0L)
                        .coerceAtLeast(chat.othersReadSeq) >= last.seq
                    Icon(if (read) Icons.Rounded.DoneAll else Icons.Rounded.Done, if (read) "Прочитано" else "Доставлено", Modifier.size(16.dp), tint = if (read) scheme.primary else scheme.outline)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatListTime(chat.lastMessage?.createdAt ?: chat.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unread > 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (chat.unread > 0) scheme.primaryContainer else scheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(typingNames.isNotEmpty(), label = "typing", transitionSpec = { fadeIn() togetherWith fadeOut() }) { isTyping ->
                        if (isTyping) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                typingNames.firstOrNull()?.takeIf { chat.type == "group" }?.let { "$it печатает" } ?: "печатает",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            TypingDots(scheme.primary, 4.dp)
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat.type != "saved" && chat.type != "channel" && chat.lastMessage != null) {
                                Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = scheme.outline)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                preview.ifEmpty {
                                    when {
                                        chat.type == "saved" -> "Сохраняйте сюда важное"
                                        chat.isService -> "Коды входа и важные новости"
                                        else -> "Нет сообщений"
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (chat.pinned && chat.unread == 0) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp), tint = scheme.outline)
                }
                UnreadBadge(chat.unread, chat.muted)
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val scale by animateFloatAsState(if (count > 0) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "unread")
    if (scale <= 0.01f) return
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(start = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(CircleShape)
            .background(if (muted) scheme.outline else scheme.primary)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(count, label = "count") { c ->
            Text(if (c > 999) "999+" else c.coerceAtLeast(1).toString(), style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary)
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyIcon(if (searching) Icons.Rounded.SearchOff else Icons.Rounded.ChatBubble)
        Spacer(Modifier.height(12.dp))
        Text(
            if (searching) "Ничего не нашлось" else "Здесь пока пусто. Нажмите на карандаш в углу, чтобы написать другу, создать группу или канал.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun subscribersText(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "подписчик"
        mod10 in 2..4 && mod100 !in 12..14 -> "подписчика"
        else -> "подписчиков"
    }
    return "$n $word"
}

private class DialItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

/** Кнопка в углу: раскрывается в «Новый чат / группа / канал» с пружинкой. */
@Composable
private fun SpeedDial(open: Boolean, expanded: Boolean, onToggle: () -> Unit, items: List<DialItem>) {
    val rotation by animateFloatAsState(if (open) 135f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "rot")
    Column(horizontalAlignment = Alignment.End) {
        items.forEachIndexed { i, item ->
            val delayMs = (items.size - 1 - i) * 40
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(androidx.compose.animation.core.tween(150, delayMs)) +
                    scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.4f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)) +
                    androidx.compose.animation.slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it },
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Row(Modifier.padding(bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 2.dp, onClick = item.onClick) {
                        Text(item.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = item.onClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) { Icon(item.icon, item.label) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onToggle,
            expanded = expanded && !open,
            icon = { Icon(Icons.Rounded.Add, null, Modifier.graphicsLayer { rotationZ = rotation }) },
            text = { Text("Создать") },
        )
    }
}


/** Круглая кнопка с иконкой для верхней строки. */
@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (primary) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (primary) scheme.onPrimary else scheme.onSurface) }
}

/** Поле поиска в виде «пилюли». */
@Composable
fun SearchPill(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(44.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
        }
        AnimatedVisibility(query.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Очистить", Modifier.size(20.dp).clip(CircleShape).clickable { onQuery("") }, tint = scheme.onSurfaceVariant)
        }
    }
}
