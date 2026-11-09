package app.ryzik.chat.ui.stories

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ryzik.chat.platform.FxMedia
import app.ryzik.chat.platform.Images
import app.ryzik.chat.platform.VideoSurface
import app.ryzik.chat.platform.parseColorInt
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.Story
import app.ryzik.chat.data.StoryGroup
import app.ryzik.chat.data.StoryViewer
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.AvatarShape
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Фоны для статусов. */
val StoryColors = listOf("#6750A4", "#E8590C", "#2F9E44", "#1971C2", "#C2255C", "#212529", "#F08C00", "#0C8599")

private val RingBrush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF9C27B0)))

fun parseColor(hex: String, fallback: Color = Color(0xFF6750A4)): Color =
    runCatching { Color(parseColorInt(hex)) }.getOrDefault(fallback)

/** Аватар с кольцом: цветное — есть непросмотренные истории, серое — всё просмотрено. */
@Composable
fun StoryAvatar(user: User, size: androidx.compose.ui.unit.Dp, hasStories: Boolean, unseen: Boolean, modifier: Modifier = Modifier) {
    val repo = RyzikApp.instance.repo
    Box(
        modifier
            .size(size)
            .let {
                when {
                    !hasStories -> it
                    unseen -> it.border(BorderStroke(2.5.dp, RingBrush), AvatarShape)
                    else -> it.border(2.dp, MaterialTheme.colorScheme.outlineVariant, AvatarShape)
                }
            }
            .padding(if (hasStories) 4.dp else 0.dp),
    ) {
        Avatar(user.displayName, repo.avatarUrl(user.avatarFileId), size - if (hasStories) 8.dp else 0.dp)
    }
}

/** Полоса историй над списком чатов. */
@Composable
fun StoriesBar(me: User?, onOpen: (index: Int) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val repo = RyzikApp.instance.repo
    val groups by repo.stories.collectAsState()
    val mine = groups.firstOrNull { it.user.id == me?.id }
    val others = groups.filter { it.user.id != me?.id }
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (me != null) item(key = "me") {
            StoryChip(
                user = me,
                label = "Моя история",
                hasStories = mine != null,
                unseen = false,
                plus = true,
                onClick = { if (mine != null) onOpen(groups.indexOf(mine)) else onAdd() },
                onPlus = onAdd,
            )
        }
        items(others, key = { it.user.id }) { g ->
            StoryChip(g.user, g.user.displayName, true, g.hasUnseen, plus = false, onClick = { onOpen(groups.indexOf(g)) })
        }
    }
}

@Composable
private fun StoryChip(user: User, label: String, hasStories: Boolean, unseen: Boolean, plus: Boolean, onClick: () -> Unit, onPlus: () -> Unit = {}) {
    Column(Modifier.width(68.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            StoryAvatar(user, 62.dp, hasStories, unseen)
            if (plus) Box(
                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(2.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onPlus),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Добавить историю", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private const val STORY_MS = 6000

/** Полноэкранный просмотр историй: тап справа — дальше, слева — назад, удержание — пауза. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerDialog(groups: List<StoryGroup>, startGroup: Int, onDismiss: () -> Unit) {
    if (groups.isEmpty()) { onDismiss(); return }
    val app = RyzikApp.instance
    val repo = app.repo
    val me by repo.auth.collectAsState()
    val myId = (me as? app.ryzik.chat.data.AuthState.LoggedIn)?.me?.id
    val scope = rememberCoroutineScope()
    var gi by remember { mutableIntStateOf(startGroup.coerceIn(0, groups.lastIndex)) }
    val group = groups.getOrNull(gi) ?: run { onDismiss(); return }
    // Начинаем с первой непросмотренной.
    var si by remember(gi) { mutableIntStateOf(group.stories.indexOfFirst { !it.seen }.takeIf { it >= 0 } ?: 0) }
    val story = group.stories.getOrNull(si) ?: run { onDismiss(); return }
    val mine = story.userId == myId
    var paused by remember { mutableStateOf(false) }
    var showViewers by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember(gi, si) { Animatable(0f) }

    fun next() {
        if (si < group.stories.lastIndex) si++
        else if (gi < groups.lastIndex) gi++
        else onDismiss()
    }
    fun prev() {
        if (si > 0) si--
        else if (gi > 0) gi--
    }

    LaunchedEffect(story.id) { repo.markStorySeen(story) }
    LaunchedEffect(gi, si, paused || showViewers) {
        if (paused || showViewers) { progress.stop(); return@LaunchedEffect }
        val left = ((1f - progress.value) * STORY_MS).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        next()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(gi, si) {
                    detectTapGestures(
                        onPress = { paused = true; tryAwaitRelease(); paused = false },
                        onTap = { o -> if (o.x < size.width / 3f) prev() else next() },
                    )
                },
        ) {
            StoryContent(story, Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.stories.forEachIndexed { i, _ ->
                        val f = when { i < si -> 1f; i > si -> 0f; else -> progress.value }
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
                            Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(group.user.displayName, repo.avatarUrl(group.user.avatarFileId), 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (mine) "Моя история" else group.user.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(storyAge(story.createdAt), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                            if (story.privacy == "contacts") Icon(Icons.Rounded.Lock, "Только контакты", Modifier.padding(start = 4.dp).size(12.dp), tint = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    if (mine) IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.deleteStory(story.id) }.onFailure { error = it.userMessage() }
                            if (group.stories.size <= 1) onDismiss() else if (si >= group.stories.lastIndex) si = (si - 1).coerceAtLeast(0)
                        }
                    }) { Icon(Icons.Rounded.Delete, "Удалить", tint = Color.White) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White) }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                if (mine) {
                    Row(
                        Modifier.align(Alignment.Center).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable { showViewers = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${story.views ?: 0}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("❤️", "🔥", "😂", "😮", "😢", "👍").forEach { e ->
                            val chosen = story.myReaction == e
                            Text(
                                e, fontSize = 26.sp,
                                modifier = Modifier.clip(CircleShape)
                                    .background(if (chosen) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        scope.launch { runCatching { repo.reactStory(story.id, if (chosen) null else e) }.onFailure { error = it.userMessage() } }
                                    }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)).padding(12.dp)) }
        }
        if (showViewers) ViewersSheet(story.id) { showViewers = false }
    }
}

@Composable
private fun StoryContent(story: Story, modifier: Modifier) {
    val repo = RyzikApp.instance.repo
    when (story.kind) {
        "image" -> Box(modifier, contentAlignment = Alignment.Center) {
            AsyncImage(repo.avatarUrl(story.fileId), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (story.text.isNotBlank()) Caption(story.text, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
        }
        "video" -> Box(modifier, contentAlignment = Alignment.Center) {
            StoryVideo(repo.avatarUrl(story.fileId) ?: "")
            if (story.text.isNotBlank()) Caption(story.text, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
        }
        else -> Box(modifier.background(parseColor(story.bg.ifBlank { StoryColors[0] })).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (story.emoji.isNotBlank()) Text(story.emoji, fontSize = 64.sp)
                Spacer(Modifier.height(12.dp))
                Text(story.text, color = Color.White, fontSize = if (story.text.length < 60) 30.sp else 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier) {
    Text(
        text, color = Color.White, textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun StoryVideo(url: String) {
    // JavaFX играет и ссылку на сервер, и локальный файл (file:/...).
    var player by remember(url) { mutableStateOf<javafx.scene.media.MediaPlayer?>(null) }
    DisposableEffect(url) {
        FxMedia.run {
            player = runCatching {
                javafx.scene.media.MediaPlayer(javafx.scene.media.Media(url)).apply {
                    cycleCount = javafx.scene.media.MediaPlayer.INDEFINITE
                    play()
                }
            }.getOrNull()
        }
        onDispose { val p = player; FxMedia.run { p?.dispose() } }
    }
    player?.let { VideoSurface(it, Modifier.fillMaxSize()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewersSheet(storyId: String, onDismiss: () -> Unit) {
    val repo = RyzikApp.instance.repo
    var viewers by remember { mutableStateOf<List<StoryViewer>?>(null) }
    LaunchedEffect(storyId) { viewers = runCatching { repo.storyViewers(storyId) }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Просмотры", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val list = viewers
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text("Пока никто не посмотрел", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(list, key = { it.user.id }) { v ->
                    ListItem(
                        headlineContent = { Text(v.user.displayName) },
                        supportingContent = { Text(storyAge(v.viewedAt)) },
                        leadingContent = { Avatar(v.user.displayName, repo.avatarUrl(v.user.avatarFileId), 42.dp) },
                        trailingContent = { v.reaction?.let { Text(it, fontSize = 22.sp) } },
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

fun storyAge(t: Long): String {
    val m = ((System.currentTimeMillis() - t) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "только что"
        m < 60 -> "$m мин назад"
        else -> "${m / 60} ч назад"
    }
}

/** Новая история: статус текстом на цветном фоне или фото/видео с подписью. */
@Composable
fun StoryComposerDialog(onDismiss: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var bg by remember { mutableStateOf(StoryColors.random()) }
    var media by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var contactsOnly by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            media = uri
            isVideo = Images.mimeOf(uri.toFile().name).startsWith("video")
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(if (media == null) parseColor(bg) else Color.Black).imePadding()) {
            media?.let { m ->
                if (isVideo) StoryVideo(m.toString())
                else AsyncImage(m, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White) }
                    Text(if (media == null) "Статус" else "История", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                        Icon(Icons.Rounded.Image, "Фото или видео", tint = Color.White)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (media == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (emoji.isNotEmpty()) Text(emoji, fontSize = 64.sp, modifier = Modifier.clickable { emoji = "" })
                        Spacer(Modifier.height(12.dp))
                        BasicTextField(
                            text, { if (it.length <= 700) text = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (text.isEmpty()) Text("Что у тебя нового?", color = Color.White.copy(alpha = 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    inner()
                                }
                            },
                        )
                    }
                }
                if (media != null) {
                    BasicTextField(
                        text, { if (it.length <= 700) text = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(14.dp),
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text("Подпись", color = Color.White.copy(alpha = 0.6f)); inner() } },
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("😊", "😎", "🔥", "📚", "🎮", "💤", "🍕", "🎧", "❤️", "🏃", "🤒", "✈️")) { e ->
                            Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = if (emoji == e) 0.35f else 0.12f)).clickable { emoji = if (emoji == e) "" else e }.padding(6.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoryColors.forEach { c ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(parseColor(c))
                                    .border(if (bg == c) 3.dp else 1.dp, Color.White, CircleShape).clickable { bg = c },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                error?.let { Text(it, color = Color.White, modifier = Modifier.padding(bottom = 8.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { contactsOnly = !contactsOnly }) {
                        Icon(if (contactsOnly) Icons.Rounded.Lock else Icons.Rounded.Public, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (contactsOnly) "Только контакты" else "Все собеседники", color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = !busy && (media != null || text.isNotBlank()),
                        onClick = {
                            busy = true; error = null
                            scope.launch {
                                runCatching {
                                    repo.postStory(
                                        kind = if (media == null) "text" else if (isVideo) "video" else "image",
                                        text = text.trim(), bg = if (media == null) bg else "", emoji = if (media == null) emoji else "",
                                        media = media, privacy = if (contactsOnly) "contacts" else "all",
                                    )
                                }.onSuccess { onDismiss() }.onFailure { error = it.userMessage() }
                                busy = false
                            }
                        },
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Опубликовать")
                    }
                }
            }
        }
    }
}
