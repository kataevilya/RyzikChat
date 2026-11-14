package app.ryzik.chat.ui.chat

import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.WavingHand
import app.ryzik.chat.notify.Notifier
import androidx.compose.ui.platform.LocalDensity
import app.ryzik.chat.platform.Platform as DesktopPlatform
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.delay
import app.ryzik.chat.ui.components.formatDuration
import app.ryzik.chat.ui.chats.subscribersText
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Call
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.SendStatus
import app.ryzik.chat.data.UiMessage
import app.ryzik.chat.data.Readers
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.VerifiedMark
import app.ryzik.chat.ui.stories.StreakBadge
import app.ryzik.chat.ui.stories.StreakInfoDialog
import app.ryzik.chat.ui.components.BadgeIcons
import app.ryzik.chat.ui.components.TypingDots
import app.ryzik.chat.ui.components.formatDay
import app.ryzik.chat.ui.components.formatLastSeen
import app.ryzik.chat.ui.components.isSameDay
import app.ryzik.chat.ui.theme.wallpaperColors
import app.ryzik.chat.ui.theme.WallpaperPicker
import app.ryzik.chat.ui.ai.AiAssistantSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import app.ryzik.chat.ui.flux.FluxIcon
import app.ryzik.chat.ui.flux.formatFlux

val QuickReactions = listOf("❤️", "👍", "😂", "🔥", "😮", "😢", "🎉", "👎", "🙏", "🤝")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
) {
    val app = RyzikApp.instance
    val repo = app.repo
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chats by repo.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    val messages by repo.messagesOf(chatId).collectAsState()
    val users by repo.users.collectAsState()
    val typing by repo.typing.collectAsState()
    val readStates by repo.readStates.collectAsState()
    val auth by repo.auth.collectAsState()
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val myId = (auth as? AuthState.LoggedIn)?.me?.id
    var streakInfo by remember { mutableStateOf(false) }
    if (streakInfo && chat != null) StreakInfoDialog(chat, myId) { streakInfo = false }

    var input by remember { mutableStateOf("") }
    // На ПК курсор сразу в поле ввода: открыл чат — и пишешь.
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(chatId) { kotlinx.coroutines.delay(350); runCatching { inputFocus.requestFocus() } }
    var replyTo by remember { mutableStateOf<UiMessage?>(null) }
    var showAi by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    val chatWallpapers by app.prefs.chatWallpapers.collectAsState(initial = emptyMap())
    var editing by remember { mutableStateOf<UiMessage?>(null) }
    var menuFor by remember { mutableStateOf<UiMessage?>(null) }
    var forwarding by remember { mutableStateOf<UiMessage?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val openedAt = remember { System.currentTimeMillis() }
    var lastTypingSent by remember { mutableLongStateOf(0L) }

    // Голосовые и квадратики
    val premium = (auth as? AuthState.LoggedIn)?.me?.isPremium == true
    val voiceLimit = if (premium) 30 * 60_000L else 10 * 60_000L
    val squareLimit = if (premium) 120_000L else 60_000L
    var recordMode by remember { mutableStateOf("voice") }
    val voiceRecorder = remember { VoiceRecorder(context) }
    val squareRecorder = remember { SquareRecorder(context) }
    var recordingVoice by remember { mutableStateOf(false) }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    var voiceElapsed by remember { mutableLongStateOf(0L) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val cancelDistance = with(LocalDensity.current) { 120.dp.toPx() }
    squareRecorder.onReady = { f, duration ->
        val (w, h) = videoSize(f)
        repo.sendRecorded(chatId, f, "square", "video/mp4", duration, width = w, height = h, replyTo = replyTo?.id)
        replyTo = null
    }
    fun finishVoice(send: Boolean) {
        if (!recordingVoice) return
        recordingVoice = false
        if (send) {
            voiceRecorder.stop()?.let { r ->
                repo.sendRecorded(chatId, r.file, "voice", "audio/wav", r.durationMs, waveform = r.waveform, replyTo = replyTo?.id)
                replyTo = null
            }
        } else voiceRecorder.cancel()
    }
    LaunchedEffect(recordingVoice) {
        while (recordingVoice) {
            voiceLevel = voiceRecorder.level()
            voiceElapsed = voiceRecorder.elapsed()
            if (voiceElapsed >= voiceLimit) finishVoice(true)
            delay(100)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (voiceRecorder.isRecording) voiceRecorder.cancel()
            InlinePlayer.stop()
        }
    }
    @Suppress("UNUSED_PARAMETER")
    fun hasPermission(p: String) = true

    // Звонки с ПК пока не поддерживаются: они есть в приложении для телефона.
    @Suppress("UNUSED_PARAMETER")
    fun startCall(video: Boolean) {
        DesktopPlatform.toast("Звонки пока доступны только в приложении для телефона")
    }

    DisposableEffect(chatId) {
        Notifier.clearChat(chatId)
        repo.openChatId = chatId
        onDispose { if (repo.openChatId == chatId) repo.openChatId = null }
    }
    LaunchedEffect(chatId) {
        launch { repo.sendErrors.collect { (c, msg) -> if (c == chatId) error = msg } }
        val c = repo.chat(chatId) ?: runCatching { repo.loadChat(chatId) }.getOrNull()
        // Сообщения группы зашифрованы для участников: до вступления их не показать.
        if (!(c?.type == "group" && c.myRole == null)) runCatching { repo.loadLatest(chatId) }.onFailure { error = it.userMessage() }
    }
    LaunchedEffect(messages.lastOrNull()?.id) { repo.markRead(chatId) }

    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }

    // Подгрузка старых сообщений при прокрутке вверх
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= reversed.size - 5 }
            .collect { runCatching { repo.loadOlder(chatId) } }
    }
    // Новое своё сообщение — прыгаем вниз
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.senderId == myId || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris: List<Uri> ->
        uris.forEachIndexed { i, uri -> repo.sendFile(chatId, uri, if (i == 0) input else "", replyTo = replyTo?.id) }
        if (uris.isNotEmpty()) { input = ""; replyTo = null }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { runCatching { repo.setWallpaper(chatId, uri) }.onFailure { error = it.userMessage() } }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { repo.sendFile(chatId, it, "", asType = "file", replyTo = replyTo?.id) }
        if (uris.isNotEmpty()) replyTo = null
    }

    val title = chat?.let { repo.chatTitle(it) } ?: ""
    val peer = chat?.let { repo.peerOf(it) }?.let { users[it.id] ?: it }
    val typingHere = typing[chatId].orEmpty().keys.filter { it != myId }
    val othersRead = (readStates[chatId].orEmpty().filterKeys { it != myId }.values.maxOrNull() ?: 0L).coerceAtLeast(chat?.othersReadSeq ?: 0L)

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        val e = editing
        if (e != null) {
            scope.launch { runCatching { repo.editMessage(e, text) }.onFailure { error = it.userMessage() } }
            editing = null
        } else {
            repo.sendText(chatId, text, replyTo?.id)
            replyTo = null
        }
        input = ""
    }

    // Обои этого чата (как в Telegram) или общие из настроек.
    val wallpaperIndex = chatWallpapers[chatId] ?: settings.wallpaper
    val wallpaper = wallpaperColors(wallpaperIndex)
    val density = LocalDensity.current
    var topH by remember { mutableIntStateOf(0) }
    var bottomH by remember { mutableIntStateOf(0) }
    val topDp = with(density) { topH.toDp() }
    val bottomDp = with(density) { bottomH.toDp() }
    val barPrefs = remember { context.getSharedPreferences("contact_bar", Context.MODE_PRIVATE) }
    var contactBarHidden by remember(chatId) { mutableStateOf(barPrefs.getBoolean(chatId, false)) }

    if (showStickers) {
        StickerSheet(chatId, replyTo?.id, onSent = { showStickers = false; replyTo = null }, onDismiss = { showStickers = false })
    }

    if (showWallpaperPicker) {
        WallpaperPicker(
            current = wallpaperIndex,
            onPick = { i ->
                scope.launch {
                    app.prefs.setChatWallpaper(chatId, i)
                    if (chat?.wallpaperFileId != null) runCatching { repo.setWallpaper(chatId, null) }
                }
            },
            onPickAll = { i ->
                scope.launch {
                    app.prefs.update { it.copy(wallpaper = i) }
                    app.prefs.clearChatWallpapers()
                    if (chat?.wallpaperFileId != null) runCatching { repo.setWallpaper(chatId, null) }
                }
                showWallpaperPicker = false
            },
            onUpload = {
                showWallpaperPicker = false
                wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = {
                scope.launch {
                    app.prefs.setChatWallpaper(chatId, null)
                    if (chat?.wallpaperFileId != null) runCatching { repo.setWallpaper(chatId, null) }
                }
                showWallpaperPicker = false
            },
            onDismiss = { showWallpaperPicker = false },
        )
    }

    if (showAi) {
        AiAssistantSheet(text = input, onApply = { input = it }, onDismiss = { showAi = false })
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(wallpaper))) {
            // Обои из фото: одни на всех участников чата. Поверх — лёгкое затемнение, чтобы читался текст.
            chat?.wallpaperFileId?.let { wp ->
                coil.compose.AsyncImage(
                    model = repo.avatarUrl(wp),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)))
            }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topDp + 8.dp, bottom = bottomDp + 8.dp),
                ) {
                    if (reversed.isEmpty()) {
                        item(key = "hello") { EmptyChat(chat?.type == "saved", chat?.type == "channel") }
                    }
                    items(reversed.size, key = { reversed[it].id }, contentType = { reversed[it].type }) { i ->
                        val m = reversed[i]
                        val older = reversed.getOrNull(i + 1)
                        val newer = reversed.getOrNull(i - 1)
                        val mine = m.senderId == myId
                        val firstInGroup = older == null || older.senderId != m.senderId || !isSameDay(older.createdAt, m.createdAt) || m.createdAt - older.createdAt > 5 * 60_000
                        val lastInGroup = newer == null || newer.senderId != m.senderId || !isSameDay(newer.createdAt, m.createdAt) || newer.createdAt - m.createdAt > 5 * 60_000
                        // Пост канала попал на экран — засчитываем просмотр.
                        if (chat?.type == "channel" && m.status == SendStatus.Sent && !m.deleted) LaunchedEffect(m.id) { repo.markViewed(chatId, m.id) }
                        val avatarSlot: (@Composable () -> Unit)? =
                            if (chat?.type == "group") { { SenderAvatar(users[m.senderId], m.senderId, onOpenProfile, onOpenChat) } } else null
                        val commentsSlot: (@Composable () -> Unit)? =
                            if (chat?.type == "channel" && chat.discussionId != null && !m.deleted && m.status == SendStatus.Sent) {
                                { CommentsButton(m.comments) { onOpenComments(m.id) } }
                            } else null
                        Column(Modifier.animateItem()) {
                            if (older == null || !isSameDay(older.createdAt, m.createdAt)) DateChip(m.createdAt)
                            val replied = m.replyTo?.let { id -> messages.firstOrNull { it.id == id } }
                            MessageBubble(
                                msg = m,
                                mine = mine,
                                sender = users[m.senderId],
                                showName = chat?.type == "group" && firstInGroup,
                                firstInGroup = firstInGroup,
                                lastInGroup = lastInGroup,
                                read = settings.showReadReceipts && m.status == SendStatus.Sent && (chat?.type == "saved" || othersRead >= m.seq),
                                replied = replied,
                                repliedSender = replied?.let { users[it.senderId]?.displayName },
                                forwardedName = m.forwardedFrom?.let { users[it]?.displayName ?: "пользователя" },
                                myId = myId,
                                style = BubbleStyle(settings.textSize, settings.bubbleRadius, settings.bigEmoji, settings.swipeToReply, settings.animations),
                                animateIn = m.createdAt > openedAt,
                                autoDownload = settings.autoDownload,
                                users = users,
                                onLongPress = { menuFor = m },
                                onDoubleTap = { if (!m.deleted && m.status == SendStatus.Sent) scope.launch { runCatching { repo.react(m, settings.quickReaction) } } },
                                onReply = { if (m.status == SendStatus.Sent) { editing = null; replyTo = m } },
                                onOpenMedia = { onOpenMedia(m.id) },
                                onReactionClick = { emoji -> scope.launch { runCatching { repo.react(m, emoji) } } },
                                onReplyClick = { id ->
                                    val idx = reversed.indexOfFirst { it.id == id }
                                    if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                },
                                onRetry = { repo.retry(m) },
                                avatar = avatarSlot,
                                footer = commentsSlot,
                            )
                        }
                    }
                }

                // Кнопка «вниз»
                val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
                val unread = chat?.unread ?: 0
                androidx.compose.animation.AnimatedVisibility(
                    showDown,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomDp + 12.dp),
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                        SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Вниз")
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    error != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topDp + 8.dp),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, onClick = { error = null }) {
                        Text(error.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Верхняя панель: отдельные «плавающие» кнопка назад, плашка с названием и аватар.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topH = it.height }
                    .background(Brush.verticalGradient(listOf(wallpaper.first().copy(alpha = 0.92f), wallpaper.first().copy(alpha = 0f))))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FloatingCircle(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onOpenInfo,
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (chat?.verified == true && peer == null) VerifiedMark(16.dp, Modifier.padding(start = 4.dp))
                                    if (peer != null) BadgeIcons(peer.badges, peer.isAdmin, 16.dp, peer.isPremium, peer.emojiStatus, peer.verified)
                                    StreakBadge(chat?.streak, Modifier.padding(start = 6.dp), onClick = { streakInfo = true })
                                }
                            AnimatedContent(
                                targetState = when {
                                    typingHere.isNotEmpty() && settings.showTyping -> "typing"
                                    else -> "status"
                                },
                                label = "subtitle",
                                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                            ) { s ->
                                if (s == "typing") Row(verticalAlignment = Alignment.CenterVertically) {
                                    val who = if (chat?.type == "group") typingHere.firstOrNull()?.let { users[it]?.displayName }?.let { "$it " } ?: "" else ""
                                    Text("${who}печатает", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    TypingDots(MaterialTheme.colorScheme.primary, 4.dp)
                                } else Text(
                                    when {
                                        chat?.isService == true -> "официальный чат"
                                        chat?.type == "direct" && peer?.isBot == true -> "бот"
                                        else -> when (chat?.type) {
                                        "saved" -> "только для вас"
                                        "group" -> "участников: ${chat.members.size}"
                                        "channel" -> subscribersText(chat.memberCount)
                                        else -> peer?.let { formatLastSeen(it.online, it.lastSeen) } ?: ""
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (peer?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            }
                            if (chat?.type == "direct" && peer != null && !peer.isBot && !chat.peerBlocked && peer.callPrivacy != "nobody") {
                                IconButton(onClick = { startCall(false) }) { Icon(Icons.Rounded.Call, "Звонок", tint = MaterialTheme.colorScheme.primary) }
                            } else Spacer(Modifier.width(12.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Surface(
                            onClick = { showMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(Modifier.padding(3.dp)) {
                                Avatar(
                                    title,
                                    repo.avatarUrl(if (chat?.type == "direct") peer?.avatarFileId else chat?.avatarFileId),
                                    46.dp,
                                    online = peer?.online == true,
                                    saved = chat?.type == "saved",
                                    service = chat?.isService == true,
                                )
                            }
                        }
                        DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (chat?.type == "direct") "Профиль" else "Информация") },
                                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                onClick = { showMenu = false; onOpenInfo() },
                            )
                            if (chat?.type == "direct" && peer != null && !peer.isBot && !chat.peerBlocked && peer.callPrivacy != "nobody") {
                                DropdownMenuItem(
                                    text = { Text("Видеозвонок") },
                                    leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                                    onClick = { showMenu = false; startCall(true) },
                                )
                            }
                            val canWallpaper = chat != null && !chat.isService &&
                                (chat.type == "direct" || chat.type == "saved" || chat.myRole == "owner" || chat.myRole == "admin")
                            if (canWallpaper) {
                                DropdownMenuItem(
                                    text = { Text("Обои чата") },
                                    leadingIcon = { Icon(Icons.Rounded.Wallpaper, null) },
                                    onClick = {
                                        showMenu = false
                                        showWallpaperPicker = true
                                    },
                                )
                                if (chat?.wallpaperFileId != null) DropdownMenuItem(
                                    text = { Text("Убрать обои") },
                                    leadingIcon = { Icon(Icons.Rounded.HideImage, null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            app.prefs.setChatWallpaper(chatId, null)
                                            runCatching { repo.setWallpaper(chatId, null) }.onFailure { error = it.userMessage() }
                                        }
                                    },
                                )
                            }
                            if (chat != null) {
                                DropdownMenuItem(
                                    text = { Text(if (chat.muted) "Включить звук" else "Без звука") },
                                    leadingIcon = { Icon(if (chat.muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null) },
                                    onClick = { showMenu = false; scope.launch { runCatching { repo.setMuted(chatId, !chat.muted) } } },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (chat.pinned) "Открепить чат" else "Закрепить чат") },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                    onClick = { showMenu = false; scope.launch { runCatching { repo.setPinned(chatId, !chat.pinned) } } },
                                )
                            }
                            if (chat?.type == "direct" && peer != null && !peer.isService) {
                                DropdownMenuItem(
                                    text = { Text(if (chat.peerBlocked) "Разблокировать" else "Заблокировать", color = if (chat.peerBlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Block, null, tint = if (chat.peerBlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch { runCatching { repo.setBlocked(peer.id, !chat.peerBlocked) }.onFailure { error = it.userMessage() } }
                                    },
                                )
                            }
                            if (chat?.type == "channel" && chat.myRole != null && chat.myRole != "owner" && !chat.isService) {
                                DropdownMenuItem(
                                    text = { Text("Отписаться") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch { runCatching { repo.leave(chatId) }.onSuccess { onBack() }.onFailure { error = it.userMessage() } }
                                    },
                                )
                            }
                        }
                    }
                }
                // Новый собеседник не в контактах: предлагаем добавить или заблокировать.
                val showContactBar = chat?.type == "direct" && peer != null && !peer.isService && !peer.isBot &&
                    !chat.peerIsContact && !chat.peerBlocked && !contactBarHidden
                AnimatedVisibility(showContactBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { peer?.let { p -> scope.launch { runCatching { repo.setContact(p.id, true) }.onFailure { error = it.userMessage() } } } },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Добавить контакт", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(
                                onClick = { peer?.let { p -> scope.launch { runCatching { repo.setBlocked(p.id, true) }.onFailure { error = it.userMessage() } } } },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Block, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Заблокировать", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { contactBarHidden = true; barPrefs.edit().putBoolean(chatId, true).apply() }) {
                                Icon(Icons.Rounded.Close, "Скрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Закреплённое сообщение: нажатие прокручивает к нему.
                val pinnedNow = chat?.pinnedMessage
                var pinnedShown by remember { mutableStateOf(pinnedNow) }
                if (pinnedNow != null) pinnedShown = pinnedNow
                AnimatedVisibility(pinnedNow != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    pinnedShown?.let { pm ->
                        val local = messages.firstOrNull { it.id == pm.id }
                        val preview = remember(pm.id, pm.editedAt, local?.content) {
                            local?.content?.let { repo.previewText(local.type, it) } ?: repo.previewOf(pm)
                        }
                        Surface(
                            onClick = {
                                scope.launch {
                                    var idx = reversed.indexOfFirst { it.id == pm.id }
                                    var tries = 0
                                    while (idx < 0 && tries < 20 && repo.canLoadMore(chatId)) {
                                        runCatching { repo.loadOlder(chatId) }
                                        idx = repo.messagesOf(chatId).value.asReversed().indexOfFirst { it.id == pm.id }
                                        tries++
                                    }
                                    if (idx >= 0) listState.animateScrollToItem(idx) else error = "Сообщение не найдено"
                                }
                            },
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Закреплённое сообщение", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (chat?.canPin == true) {
                                    IconButton(onClick = { scope.launch { runCatching { repo.pinMessage(chatId, null) }.onFailure { error = it.userMessage() } } }) {
                                        Icon(Icons.Rounded.Close, "Открепить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Icon(Icons.Rounded.PushPin, null, Modifier.padding(12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Нижняя панель: отдельные кнопка вложений, поле ввода и кнопка отправки.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomH = it.height }) {
            val canPost = chat == null || (chat.type != "channel" && (chat.myRole != null || chat.type == "direct" || chat.type == "saved")) ||
                chat.myRole == "owner" || chat.myRole == "admin"
            if (chat?.peerBlocked == true) {
                BlockedBar(onUnblock = {
                    peer?.let { p -> scope.launch { runCatching { repo.setBlocked(p.id, false) }.onFailure { error = it.userMessage() } } }
                })
            } else if (chat?.banned == true) {
                ModerationBar(
                    "Чат заблокирован модерацией" + chat.banReason.takeIf { it.isNotBlank() }?.let { ". Причина: $it" }.orEmpty(),
                )
            } else if (canPost && (auth as? AuthState.LoggedIn)?.me?.restrictedUntil != null) {
                val meNow = (auth as AuthState.LoggedIn).me
                ModerationBar(
                    "Аккаунт ограничен: писать сообщения пока нельзя" + meNow.restrictReason.takeIf { it.isNotBlank() }?.let { ". Причина: $it" }.orEmpty(),
                )
            } else if (!canPost && chat != null) {
                ChannelBar(
                    subscribed = chat.myRole != null,
                    joinText = if (chat.type == "group") "Вступить в группу" else "Подписаться",
                    muted = chat.muted,
                    onSubscribe = { scope.launch { runCatching { repo.subscribe(chatId); repo.loadLatest(chatId) }.onFailure { error = it.userMessage() } } },
                    onToggleMute = { scope.launch { runCatching { repo.setMuted(chatId, !chat.muted) } } },
                )
            } else
            // Панель ответа/редактирования
            Box {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    val meNow = (auth as? AuthState.LoggedIn)?.me
                    // Бот: кнопка «Начать» в пустом чате и кнопки его команд над полем ввода.
                    if (chat?.type == "direct" && peer?.isBot == true) {
                        if (messages.none { it.senderId == myId }) {
                            Button(
                                onClick = { repo.sendText(chatId, "/start") },
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            ) { Text("Начать") }
                        } else if (peer.botCommands.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            ) {
                                items(peer.botCommands, key = { it.command }) { c ->
                                    Surface(
                                        onClick = { repo.sendText(chatId, "/" + c.command) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shadowElevation = 2.dp,
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text("/" + c.command, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                            if (c.description.isNotBlank()) Text(
                                                c.description, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (chat?.type == "direct" && peer != null && peer.messagePrice > 0 && meNow?.isAdmin != true) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FluxIcon(18.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Сообщение стоит ${peer.messagePrice} FLUX, если вас нет в контактах у ${peer.displayName}. У вас ${formatFlux(meNow?.flux ?: 0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    AnimatedVisibility(replyTo != null || editing != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val target = editing ?: replyTo
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (editing != null) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            if (target != null) {
                                ReplyQuote(
                                    name = if (editing != null) "Редактирование" else users[target.senderId]?.displayName ?: "",
                                    text = target.content?.let { previewOf(target.type, it.text, it.file) } ?: "",
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IconButton(onClick = { if (editing != null) input = ""; replyTo = null; editing = null }) {
                                Icon(Icons.Rounded.Close, "Отмена")
                            }
                        }
                        }
                    }
                    // AI появляется, когда текста уже три строки (или он совсем длинный).
                    val aiEligible = input.lines().size >= 3 || input.length >= 180
                    AnimatedVisibility(aiEligible && !recordingVoice, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { showAi = true },
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("✨", fontSize = 17.sp)
                                    Spacer(Modifier.width(7.dp))
                                    Text("AI-помощник", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box {
                            FloatingCircle(onClick = { showAttach = true }) { Icon(Icons.Rounded.AttachFile, "Прикрепить") }
                            DropdownMenu(showAttach, onDismissRequest = { showAttach = false }) {
                                DropdownMenuItem(
                                    text = { Text("Фото или видео") },
                                    leadingIcon = { Icon(Icons.Rounded.Photo, null) },
                                    onClick = {
                                        showAttach = false
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Файл") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null) },
                                    onClick = { showAttach = false; filePicker.launch(arrayOf("*/*")) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Стикеры") },
                                    leadingIcon = { Icon(Icons.Rounded.EmojiEmotions, null) },
                                    onClick = { showAttach = false; showStickers = true },
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp,
                            modifier = Modifier.weight(1f),
                        ) {
                        if (recordingVoice) {
                            VoiceRecordingBar(voiceElapsed, dragX, cancelDistance, Modifier.fillMaxWidth().height(52.dp))
                        } else TextField(
                            value = input,
                            onValueChange = {
                                input = it
                                val now = System.currentTimeMillis()
                                if (it.isNotBlank() && now - lastTypingSent > 3000) {
                                    lastTypingSent = now
                                    repo.sendTyping(chatId)
                                }
                            },
                            placeholder = { Text("Сообщение") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 160.dp).focusRequester(inputFocus).onPreviewKeyEvent { e ->
                                // Enter — отправить, Shift+Enter — новая строка.
                                if (settings.sendByEnter && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                    (e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.NumPadEnter) && !e.isShiftPressed
                                ) {
                                    if (input.isNotBlank()) send()
                                    true
                                } else if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.V && (e.isCtrlPressed || e.isMetaPressed)) {
                                    // Ctrl+V с картинкой или файлами в буфере — отправляем их, текст вставляется как обычно.
                                    DesktopPlatform.pastedFiles()?.let { files ->
                                        files.forEach { repo.sendFile(chatId, android.net.Uri.fromFile(it), "", replyTo = replyTo?.id) }
                                        replyTo = null
                                        true
                                    } ?: false
                                } else false
                            },
                            shape = RoundedCornerShape(26.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = if (settings.sendByEnter) ImeAction.Send else ImeAction.Default,
                            ),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            maxLines = 6,
                        )
                        }
                        Spacer(Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = input.isNotBlank(),
                            label = "send",
                            transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                        ) { canSend ->
                            if (canSend) FilledIconButton(onClick = { send() }, modifier = Modifier.size(52.dp)) {
                                Icon(if (editing != null) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send, "Отправить")
                            } else RecordButton(
                                mode = recordMode,
                                recording = recordingVoice || squareRecorder.active,
                                level = voiceLevel,
                                // На ПК квадратики не записываются: кнопка всегда пишет голосовое.
                                onTap = { DesktopPlatform.toast("Зажмите кнопку, чтобы записать голосовое") },
                                onStart = {
                                    if (false) {
                                        false
                                    } else if (recordMode == "voice") {
                                        InlinePlayer.stop()
                                        recordingVoice = voiceRecorder.start()
                                        if (!recordingVoice) error = "Не удалось включить микрофон"
                                        recordingVoice
                                    } else {
                                        InlinePlayer.stop()
                                        squareRecorder.begin()
                                        true
                                    }
                                },
                                onDrag = { dragX = it },
                                onEnd = { cancel ->
                                    dragX = 0f
                                    if (recordMode == "voice") finishVoice(!cancel)
                                    else {
                                        if (!cancel && squareRecorder.elapsed() < 800) error = "Держите кнопку, пока идёт запись квадратика"
                                        squareRecorder.finish(send = !cancel)
                                    }
                                },
                                cancelDistance = cancelDistance,
                            )
                        }
                    }
                }
            }
        }
    }

    if (squareRecorder.active) {
        SquareRecordingOverlay(squareRecorder, squareLimit, (-dragX / cancelDistance).coerceIn(0f, 1f))
    }

    // Меню сообщения
    menuFor?.let { m ->
        val mine = m.senderId == myId
        ModalBottomSheet(onDismissRequest = { menuFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Пунктов много: на низком экране список прокручивается, а не обрезается.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!m.deleted && m.status == SendStatus.Sent) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(QuickReactions) { e ->
                            val selected = m.reactions.any { it.userId == myId && it.emoji == e }
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        menuFor = null
                                        scope.launch { runCatching { repo.react(m, e) } }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 24.sp) }
                        }
                    }
                    HorizontalDivider()
                }
                if (m.status == SendStatus.Failed) {
                    SheetItem("Отправить ещё раз", Icons.Rounded.Refresh) { menuFor = null; repo.retry(m) }
                }
                // Кто поставил реакции.
                if (m.reactions.isNotEmpty()) {
                    var showReactors by remember(m.id) { mutableStateOf(false) }
                    SheetItem(
                        "Реакции: ${m.reactions.size} · " + m.reactions.groupBy { it.emoji }.entries.joinToString(" ") { (e, l) -> if (l.size > 1) "$e ${l.size}" else e },
                        Icons.Rounded.FavoriteBorder,
                    ) { showReactors = !showReactors }
                    if (showReactors) {
                        LaunchedEffect(m.id) {
                            m.reactions.map { it.userId }.filter { users[it] == null }.distinct().forEach { id -> runCatching { repo.loadUser(id) } }
                        }
                        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                            m.reactions.forEach { r ->
                                val u = users[r.userId]
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u?.displayName ?: "?", repo.avatarUrl(u?.avatarFileId), 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (r.userId == myId) "Вы" else u?.displayName ?: "…", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                // Своё сообщение: кто его прочитал.
                if (mine && !m.deleted && m.status == SendStatus.Sent && chat != null && chat.type != "saved") {
                    var readers by remember(m.id) { mutableStateOf<Readers?>(null) }
                    var showReaders by remember(m.id) { mutableStateOf(false) }
                    LaunchedEffect(m.id) { readers = runCatching { repo.api.readers(m.id) }.getOrNull() }
                    val r = readers
                    SheetItem(
                        when {
                            r == null -> "Загружаю, кто прочитал…"
                            r.count == 0 -> "Ещё никто не прочитал"
                            chat.type == "direct" -> "Прочитано"
                            else -> "Прочитали: ${r.count}"
                        },
                        Icons.Rounded.DoneAll,
                    ) { if (r != null && r.count > 0 && chat.type != "direct") showReaders = !showReaders }
                    if (showReaders && r != null) {
                        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                            r.users.forEach { u ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(u.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (r.count > r.users.size) Text("и ещё ${r.count - r.users.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                if (!m.deleted && m.status == SendStatus.Sent) {
                    SheetItem("Ответить", Icons.AutoMirrored.Rounded.Reply) { menuFor = null; editing = null; replyTo = m }
                }
                if (!m.deleted && m.status == SendStatus.Sent && m.commentOf == null && chat?.canPin == true) {
                    val isPinned = chat.pinnedMessage?.id == m.id
                    SheetItem(if (isPinned) "Открепить" else "Закрепить", Icons.Rounded.PushPin) {
                        menuFor = null
                        scope.launch { runCatching { repo.pinMessage(chatId, if (isPinned) null else m.id) }.onFailure { error = it.userMessage() } }
                    }
                }
                if (!m.content?.text.isNullOrEmpty()) {
                    SheetItem("Копировать текст", Icons.Rounded.ContentCopy) {
                        menuFor = null
                        copy(context, m.content!!.text)
                    }
                }
                if (mine && !m.deleted && m.status == SendStatus.Sent && m.content != null) {
                    SheetItem("Изменить", Icons.Rounded.Edit) {
                        menuFor = null
                        replyTo = null
                        editing = m
                        input = m.content.text
                    }
                }
                if (!m.deleted && m.content != null && m.status == SendStatus.Sent) {
                    if (chat?.type != "saved") {
                        SheetItem("В Избранное", Icons.Rounded.Bookmark) { menuFor = null; repo.saveToFavorites(m) }
                    }
                    SheetItem("Переслать", Icons.Rounded.Share) { menuFor = null; forwarding = m }
                }
                val me = (auth as? AuthState.LoggedIn)?.me
                val canDelete = mine || me?.isAdmin == true || chat?.myRole == "owner" || (chat?.type == "channel" && chat.myRole == "admin") ||
                    chat?.members?.any { it.user.id == myId && it.role == "owner" } == true
                if (!m.deleted && canDelete) {
                    SheetItem("Удалить", Icons.Rounded.Delete, danger = true) {
                        menuFor = null
                        scope.launch { runCatching { repo.deleteMessage(m) }.onFailure { error = it.userMessage() } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    forwarding?.let { m ->
        ModalBottomSheet(onDismissRequest = { forwarding = null }) {
            Text("Переслать в…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(chats, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(repo.chatTitle(c)) },
                        leadingContent = {
                            Avatar(repo.chatTitle(c), repo.avatarUrl(if (c.type == "direct") repo.peerOf(c)?.avatarFileId else c.avatarFileId), 40.dp, saved = c.type == "saved")
                        },
                        modifier = Modifier.clickable {
                            forwarding = null
                            repo.forward(m, c.id)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(text, color = color) },
        leadingContent = { Icon(icon, null, tint = color) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DateChip(t: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
            Text(formatDay(t), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

/** Плашка вместо поля ввода: чат заблокирован или аккаунт ограничен. */
@Composable
private fun ModerationBar(text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Gavel, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EmptyChat(saved: Boolean, channel: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                app.ryzik.chat.ui.components.EmptyIcon(if (saved) Icons.Rounded.Bookmark else if (channel) Icons.Rounded.Campaign else Icons.Rounded.WavingHand)
                Spacer(Modifier.height(8.dp))
                Text(if (saved) "Избранное" else if (channel) "В канале пока нет постов" else "Пока тихо", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        saved -> "Пересылайте сюда сообщения, фото и файлы, чтобы не потерять."
                        channel -> "Здесь появятся посты, голосовые и квадратики владельца канала."
                        else -> "Напишите первым! Сообщения защищены сквозным шифрованием."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun copy(context: Context, text: String) = app.ryzik.chat.platform.Platform.copy(text)

/** Круглая «плавающая» кнопка с тенью. */
@Composable
private fun FloatingCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.size(52.dp),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Вместо поля ввода, если собеседник заблокирован. */
@Composable
private fun BlockedBar(onUnblock: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Пользователь заблокирован", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onUnblock) { Text("Разблокировать") }
            }
        }
    }
}

@Composable
private fun ChannelBar(subscribed: Boolean, joinText: String, muted: Boolean, onSubscribe: () -> Unit, onToggleMute: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(subscribed, label = "sub", transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith fadeOut() }) { sub ->
                if (!sub) {
                    Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(joinText)
                    }
                } else {
                    TextButton(onClick = onToggleMute, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(if (muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (muted) "Включить звук" else "Выключить звук")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingBar(elapsed: Long, dragX: Float, cancelDistance: Float, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hint")
    val nudge by t.animateFloat(0f, -10f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "nudge")
    val cancelP = (-dragX / cancelDistance).coerceIn(0f, 1f)
    Row(modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RecordingDot()
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(elapsed), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text(
            "‹ Влево — отмена",
            color = if (cancelP > 0.6f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                translationX = dragX * 0.6f + nudge * density
                alpha = 1f - cancelP * 0.5f
            },
        )
        Spacer(Modifier.width(12.dp))
    }
}

/**
 * Кнопка записи: нажатие переключает «голосовое» ↔ «квадратик»,
 * удержание записывает, отпускание отправляет, свайп влево отменяет.
 */
@Composable
private fun RecordButton(
    mode: String,
    recording: Boolean,
    level: Float,
    onTap: () -> Unit,
    onStart: () -> Boolean,
    onDrag: (Float) -> Unit,
    onEnd: (cancel: Boolean) -> Unit,
    cancelDistance: Float,
) {
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (recording) 1.5f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "rec")
    val pulse by animateFloatAsState(if (recording) 1f + level * 0.6f else 1f, label = "pulse")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        if (recording) {
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer { scaleX = scale * pulse; scaleY = scale * pulse }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var released = false
                        withTimeoutOrNull(250) {
                            waitForUpOrCancellation()
                            released = true
                        }
                        if (released) { tap(); return@awaitEachGesture }
                        if (!start()) { waitForUpOrCancellation(); return@awaitEachGesture }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var cancelled = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val dx = (ch.position.x - down.position.x).coerceAtMost(0f)
                            drag(dx)
                            ch.consume()
                            if (dx < -cancelDistance) { cancelled = true; break }
                        }
                        end(cancelled)
                        if (cancelled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            waitForUpOrCancellation()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                mode,
                label = "mode",
                transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            ) { m ->
                Icon(
                    if (m == "voice") Icons.Rounded.Mic else Icons.Rounded.CropSquare,
                    if (m == "voice") "Голосовое" else "Квадратик",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
