package app.ryzik.chat.ui.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.UiMessage
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import kotlinx.coroutines.launch

/**
 * Аватарка отправителя в группе. Нажатие — меню: открыть профиль или написать в личку.
 */
@Composable
fun SenderAvatar(user: User?, userId: String, onOpenProfile: (String) -> Unit, onOpenChat: (String) -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Box {
        Avatar(
            user?.displayName ?: "?",
            repo.avatarUrl(user?.avatarFileId),
            34.dp,
            Modifier.clip(CircleShape).clickable { menu = true },
        )
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            Text(
                user?.displayName ?: "Пользователь",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DropdownMenuItem(
                text = { Text("Открыть профиль") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { menu = false; onOpenProfile(userId) },
            )
            if (userId != repo.myId) DropdownMenuItem(
                text = { Text("Написать сообщение") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) },
                onClick = {
                    menu = false
                    scope.launch { runCatching { repo.openDirect(userId) }.onSuccess { onOpenChat(it.id) } }
                },
            )
        }
    }
}

/** Кнопка под постом канала: «N комментариев». */
@Composable
fun CommentsButton(count: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (count == 0) "Прокомментировать" else commentsText(count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

fun commentsText(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "комментариев"
        n % 10 == 1 -> "комментарий"
        n % 10 in 2..4 -> "комментария"
        else -> "комментариев"
    }
    return "$n $word"
}

/** Комментарии к посту канала: пост сверху, ниже обсуждение из привязанной группы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(postId: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit, onOpenChat: (String) -> Unit) {
    val app = RyzikApp.instance
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val users by repo.users.collectAsState()
    val auth by repo.auth.collectAsState()
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val myId = (auth as? AuthState.LoggedIn)?.me?.id

    var post by remember { mutableStateOf<UiMessage?>(null) }
    var comments by remember { mutableStateOf<List<UiMessage>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun add(m: UiMessage) {
        if (comments.none { it.id == m.id }) comments = comments + m
    }

    LaunchedEffect(postId) {
        runCatching { repo.loadComments(postId) }
            .onSuccess { (p, list) -> post = p; comments = list }
            .onFailure { error = it.userMessage() }
    }
    LaunchedEffect(postId) {
        repo.commentEvents.collect { ev ->
            if (ev.messageId == postId) ev.message?.let { add(repo.decryptComment(it)) }
        }
    }
    LaunchedEffect(comments.size) { if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size) }

    val style = BubbleStyle(settings.textSize, settings.bubbleRadius, settings.bigEmoji, false, settings.animations)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (comments.isEmpty()) "Комментарии" else commentsText(comments.size)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    input, { input = it },
                    placeholder = { Text("Комментарий") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    enabled = input.isNotBlank() && !sending && post != null,
                    onClick = {
                        val text = input
                        sending = true
                        scope.launch {
                            runCatching { repo.sendComment(postId, text) }
                                .onSuccess { input = ""; add(it); error = null }
                                .onFailure { error = it.userMessage() }
                            sending = false
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить", tint = MaterialTheme.colorScheme.primary) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "post") {
                Column {
                    post?.let { p ->
                        MessageBubble(
                            msg = p, mine = false, sender = null, showName = false, firstInGroup = true, lastInGroup = true,
                            read = false, replied = null, repliedSender = null, forwardedName = null, myId = myId,
                            style = style, animateIn = false, autoDownload = settings.autoDownload, users = users,
                            onLongPress = {}, onDoubleTap = {}, onReply = {}, onOpenMedia = {}, onReactionClick = {}, onReplyClick = {}, onRetry = {},
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    if (post != null && comments.isEmpty()) Text(
                        "Комментариев пока нет. Будьте первым!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(comments.size, key = { comments[it].id }) { i ->
                val m = comments[i]
                val older = comments.getOrNull(i - 1)
                val newer = comments.getOrNull(i + 1)
                val firstInGroup = older == null || older.senderId != m.senderId
                val lastInGroup = newer == null || newer.senderId != m.senderId
                MessageBubble(
                    msg = m, mine = m.senderId == myId, sender = users[m.senderId], showName = firstInGroup,
                    firstInGroup = firstInGroup, lastInGroup = lastInGroup, read = false, replied = null, repliedSender = null,
                    forwardedName = null, myId = myId, style = style, animateIn = false, autoDownload = settings.autoDownload, users = users,
                    onLongPress = {}, onDoubleTap = {}, onReply = {}, onOpenMedia = {}, onReactionClick = {}, onReplyClick = {}, onRetry = {},
                    avatar = { SenderAvatar(users[m.senderId], m.senderId, onOpenProfile, onOpenChat) },
                )
            }
        }
    }
}
