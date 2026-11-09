package app.ryzik.chat.ui.chat

import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.FileRef
import app.ryzik.chat.data.MediaState
import app.ryzik.chat.data.SendStatus
import app.ryzik.chat.data.UiMessage
import app.ryzik.chat.data.User
import app.ryzik.chat.ui.components.formatDuration
import app.ryzik.chat.ui.components.formatSize
import app.ryzik.chat.ui.components.formatTime
import app.ryzik.chat.ui.components.parseColor
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val nameColors = listOf(
    Color(0xFFE17076), Color(0xFF7BC862), Color(0xFF65AADD), Color(0xFFA695E7),
    Color(0xFFEE7AAE), Color(0xFF6EC9CB), Color(0xFFFAA74A),
)

data class BubbleStyle(val textSize: Float, val radius: Float, val bigEmoji: Boolean, val swipeToReply: Boolean, val animate: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: UiMessage,
    mine: Boolean,
    sender: User?,
    showName: Boolean,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    read: Boolean,
    replied: UiMessage?,
    repliedSender: String?,
    forwardedName: String?,
    myId: String?,
    style: BubbleStyle,
    animateIn: Boolean,
    autoDownload: Boolean,
    users: Map<String, User>,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onReply: () -> Unit,
    onOpenMedia: () -> Unit,
    onReactionClick: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onRetry: () -> Unit,
    /** Аватарка отправителя слева (в группах); рисуется у последнего сообщения подряд. */
    avatar: (@Composable () -> Unit)? = null,
    /** Под пузырём: например, кнопка комментариев у поста канала. */
    footer: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Появление нового сообщения: выезжает снизу с пружинкой.
    val appear = remember { Animatable(if (animateIn && style.animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }

    // Свайп влево — ответить.
    val drag = remember { Animatable(0f) }
    val replyThreshold = 64f * LocalContext.current.resources.displayMetrics.density

    val r = style.radius.dp
    val small = (style.radius / 3.5f).dp
    val shape = if (mine) RoundedCornerShape(r, if (firstInGroup) r else small, if (lastInGroup) small else small, r)
    else RoundedCornerShape(if (firstInGroup) r else small, r, r, if (lastInGroup) small else small)

    val bubbleColor = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh
    val onBubble = if (mine) scheme.onPrimaryContainer else scheme.onSurface
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp

    val c = msg.content
    val emojiOnly = style.bigEmoji && msg.type == "text" && c != null && isEmojiOnly(c.text)
    val isSquare = (msg.type == "square" || (msg.type == "video" && c?.file?.square == true)) && c?.file != null && !msg.deleted
    val isMedia = (msg.type == "image" || msg.type == "video") && c?.file != null && !isSquare
    val isSticker = msg.type == "sticker" && c?.sticker != null && !msg.deleted
    val bare = emojiOnly || isSquare || isSticker
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (firstInGroup) 6.dp else 1.5.dp, bottom = 1.5.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 60f
                scaleX = 0.85f + 0.15f * appear.value
                scaleY = 0.85f + 0.15f * appear.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
            }
            .pointerInput(style.swipeToReply, msg.id) {
                if (!style.swipeToReply || msg.deleted) return@pointerInput
                var fired = false
                detectHorizontalDragGestures(
                    onDragStart = { fired = false },
                    onDragEnd = {
                        if (abs(drag.value) >= replyThreshold) onReply()
                        scope.launch { drag.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { _, dx ->
                    val next = (drag.value + dx).coerceIn(-replyThreshold * 1.4f, 0f)
                    if (!fired && abs(next) >= replyThreshold) {
                        fired = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    scope.launch { drag.snapTo(next) }
                }
            },
    ) {
        // Иконка «ответить», проявляется при свайпе
        val progress = (abs(drag.value) / replyThreshold).coerceIn(0f, 1f)
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .graphicsLayer { alpha = progress; scaleX = progress; scaleY = progress }
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp), tint = scheme.onSecondaryContainer) }
        }

        val withAvatar = avatar != null && !mine
        Row(
            Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .padding(horizontal = if (withAvatar) 6.dp else 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
        if (withAvatar) {
            Box(Modifier.padding(bottom = if (footer != null) 40.dp else 2.dp).size(34.dp)) { if (lastInGroup) avatar?.invoke() }
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier.widthIn(max = if (withAvatar) maxWidth - 40.dp else maxWidth),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            val bubbleModifier = Modifier
                .clip(shape)
                .then(if (bare) Modifier else Modifier.background(bubbleColor))
                .combinedClickable(
                    onClick = {
                        when {
                            msg.status == SendStatus.Failed -> onRetry()
                            isSquare -> toggleSquare(context, msg)
                            isSticker -> StickerViewer.open(c!!.sticker!!.packId)
                            isMedia -> onOpenMedia()
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                    onDoubleClick = onDoubleTap,
                )
                .animateContentSize()

            Column(bubbleModifier.padding(if (isMedia) 4.dp else 0.dp)) {
                if (showName && sender != null && !mine) {
                    // Имя отправителя и его значки: эмодзи-статус, галочка, щит админа, бейджи.
                    Row(
                        Modifier.padding(start = if (isMedia) 8.dp else 12.dp, end = 12.dp, top = if (isMedia) 4.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sender.displayName,
                            color = nameColors[Math.floorMod(sender.id.hashCode(), nameColors.size)],
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        app.ryzik.chat.ui.components.BadgeIcons(sender.badges, sender.isAdmin, 14.dp, sender.isPremium, sender.emojiStatus, sender.verified)
                    }
                }
                if (forwardedName != null) {
                    Text(
                        "Переслано от $forwardedName",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                }
                if (msg.replyTo != null) {
                    ReplyQuote(
                        name = repliedSender ?: "Сообщение",
                        text = replied?.let { rm -> rm.content?.let { previewOf(rm.type, it.text, it.file) } ?: if (rm.deleted) "Удалено" else "Сообщение" } ?: "Сообщение",
                        accent = if (mine) scheme.primary else scheme.tertiary,
                        modifier = Modifier
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp)
                            .clickable { onReplyClick(msg.replyTo) },
                    )
                }

                when {
                    msg.deleted -> InfoText("Сообщение удалено", onBubble)
                    c == null -> InfoText("Не удалось расшифровать", onBubble)
                    isSticker && c.sticker != null -> coil.compose.AsyncImage(
                        model = app.ryzik.chat.RyzikApp.instance.repo.avatarUrl(c.sticker.fileId),
                        contentDescription = "Стикер",
                        modifier = Modifier.size(150.dp),
                    )
                    isSquare && c.file != null -> SquareContent(msg, c.file, autoDownload)
                    msg.type == "image" && c.file != null -> MediaImage(msg, c.file, autoDownload)
                    msg.type == "video" && c.file != null -> MediaVideo(msg, c.file, autoDownload)
                    msg.type == "file" && c.file != null -> FileAttachment(msg, c.file, onBubble, autoDownload)
                    msg.type == "voice" && c.file != null -> VoiceContent(msg, c.file, onBubble, if (mine) scheme.primary else scheme.tertiary)
                    msg.type == "gift" && c.gift != null -> GiftContent(c.gift, mine, onBubble)
                    msg.type == "call" && c.call != null -> CallContent(c.call, mine, onBubble)
                    else -> Unit
                }

                val text = c?.text.orEmpty()
                val showMeta = !msg.deleted
                if (emojiOnly) {
                    Text(text, fontSize = 48.sp, lineHeight = 56.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    MetaRow(msg, mine, read, scheme.onSurfaceVariant, Modifier.align(Alignment.End).padding(end = 4.dp))
                } else if (text.isNotEmpty() && !msg.deleted) {
                    Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                        Text(
                            text + "    " + (if (msg.editedAt != null) "   " else "") + (if (msg.views != null) "     " else ""),
                            color = onBubble,
                            fontSize = style.textSize.sp,
                            lineHeight = (style.textSize * 1.35f).sp,
                        )
                        MetaRow(msg, mine, read, onBubble.copy(alpha = 0.65f), Modifier.align(Alignment.BottomEnd))
                    }
                    // Ссылка на набор стикеров — кнопка, чтобы открыть его.
                    STICKER_LINK.find(text)?.let { m ->
                        Text(
                            "Открыть набор стикеров",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (mine) scheme.onPrimary else scheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                                .clip(CircleShape)
                                .background(if (mine) scheme.primary else scheme.primaryContainer)
                                .clickable { StickerViewer.open(m.groupValues[1]) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                } else if (showMeta || msg.deleted) {
                    MetaRow(
                        msg, mine, read,
                        if (isMedia || isSquare || isSticker) Color.White else onBubble.copy(alpha = 0.65f),
                        Modifier
                            .align(Alignment.End)
                            .padding(6.dp)
                            .then(if (isMedia || isSquare || isSticker) Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier),
                    )
                }
            }

            if (msg.reactions.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    msg.reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                        val minePick = list.any { it.userId == myId }
                        ReactionPill(emoji, list.size, minePick, list.mapNotNull { users[it.userId]?.displayName }) { onReactionClick(emoji) }
                    }
                }
            }
            footer?.invoke()
        }
        }
    }
}

@Composable
private fun ReactionPill(emoji: String, count: Int, mine: Boolean, @Suppress("UNUSED_PARAMETER") names: List<String>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(count, mine) {
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (mine) scheme.primary else scheme.secondaryContainer,
        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp)
            if (count > 1) {
                Spacer(Modifier.width(4.dp))
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = if (mine) scheme.onPrimary else scheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun InfoText(text: String, color: Color) {
    Text(text, color = color.copy(alpha = 0.7f), fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp))
}

@Composable
fun ReplyQuote(name: String, text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(name, color = accent, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 1234 → «1,2K», 2 500 000 → «2,5M». */
fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M").replace('.', ',')
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000f).replace(".0K", "K").replace('.', ',')
    else -> n.toString()
}

@Composable
private fun MetaRow(msg: UiMessage, mine: Boolean, read: Boolean, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (msg.editedAt != null) {
            Text("изм. ", style = MaterialTheme.typography.labelSmall, color = color)
        }
        msg.views?.let { v ->
            Icon(Icons.Rounded.Visibility, "Просмотры", Modifier.size(13.dp), tint = color)
            Text(" ${compactCount(v)}  ", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Text(formatTime(msg.createdAt), style = MaterialTheme.typography.labelSmall, color = color)
        if (mine && msg.views == null) {
            Spacer(Modifier.width(3.dp))
            val icon = when (msg.status) {
                SendStatus.Sending -> Icons.Rounded.Schedule
                SendStatus.Failed -> Icons.Rounded.ErrorOutline
                SendStatus.Sent -> if (read) Icons.Rounded.DoneAll else Icons.Rounded.Done
            }
            val tint = when {
                msg.status == SendStatus.Failed -> MaterialTheme.colorScheme.error
                read -> MaterialTheme.colorScheme.primary
                else -> color
            }
            androidx.compose.animation.AnimatedContent(icon, label = "status") { ic ->
                Icon(ic, null, Modifier.size(15.dp), tint = tint)
            }
        }
    }
}

// ---------- Медиа ----------

@Composable
fun mediaStateOf(file: FileRef): androidx.compose.runtime.State<MediaState> {
    val repo = RyzikApp.instance.repo
    val flow = remember(file.id) {
        if (file.id.isEmpty()) kotlinx.coroutines.flow.MutableStateFlow<MediaState>(MediaState.Idle) else repo.mediaState(file.id)
    }
    return flow.collectAsState()
}

@Composable
private fun rememberVideoLoader(): ImageLoader {
    val context = LocalContext.current
    return remember { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
}

@Composable
private fun MediaBox(file: FileRef, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val ratio = if (file.width > 0 && file.height > 0) (file.width.toFloat() / file.height).coerceIn(0.5f, 2f) else 1.3f
    Box(
        Modifier
            .width(260.dp)
            .aspectRatio(ratio)
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MediaImage(msg: UiMessage, file: FileRef, autoDownload: Boolean) {
    val repo = RyzikApp.instance.repo
    val state by mediaStateOf(file)
    LaunchedEffect(file.id, autoDownload) { if (autoDownload && msg.localFile == null) repo.download(file) }
    MediaBox(file) {
        val local = msg.localFile ?: (state as? MediaState.Ready)?.file
        if (local != null) {
            AsyncImage(model = local, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        MediaOverlay(msg, file, state, local != null, isVideo = false)
    }
}

@Composable
private fun MediaVideo(msg: UiMessage, file: FileRef, autoDownload: Boolean) {
    val repo = RyzikApp.instance.repo
    val state by mediaStateOf(file)
    LaunchedEffect(file.id, autoDownload) { if (autoDownload && msg.localFile == null && file.size < 50L * 1024 * 1024) repo.download(file) }
    val loader = rememberVideoLoader()
    val context = LocalContext.current
    MediaBox(file) {
        val local = msg.localFile ?: (state as? MediaState.Ready)?.file
        if (local != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(local).videoFrameMillis(500).build(),
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MediaOverlay(msg, file, state, local != null, isVideo = true)
        Text(
            formatDuration(file.durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MediaOverlay(msg: UiMessage, file: FileRef, state: MediaState, ready: Boolean, isVideo: Boolean) {
    val repo = RyzikApp.instance.repo
    val uploading = msg.status == SendStatus.Sending
    when {
        uploading -> CircleProgress(msg.uploadProgress ?: 0f)
        state is MediaState.Loading -> CircleProgress(state.progress)
        !ready -> Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { repo.download(file) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Download, "Скачать", tint = Color.White)
            }
        }
        isVideo -> Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, "Смотреть", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
    if (!ready && state !is MediaState.Loading && !uploading) {
        Text(
            formatSize(file.size),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 80.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CircleProgress(p: Float) {
    val animated by animateFloatAsState(p, label = "progress")
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        if (p <= 0f) CircularProgressIndicator(Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
        else CircularProgressIndicator(progress = { animated }, modifier = Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
    }
}

@Composable
private fun FileAttachment(msg: UiMessage, file: FileRef, color: Color, autoDownload: Boolean) {
    val repo = RyzikApp.instance.repo
    val context = LocalContext.current
    val state by mediaStateOf(file)
    LaunchedEffect(file.id) { if (autoDownload && file.size < 5L * 1024 * 1024 && msg.localFile == null) repo.download(file) }
    val local = msg.localFile ?: (state as? MediaState.Ready)?.file
    Row(
        Modifier
            .padding(start = 8.dp, end = 12.dp, top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (local != null) openFile(context, local, file) else repo.download(file)
            }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            val loading = msg.status == SendStatus.Sending || state is MediaState.Loading
            if (loading) {
                val p = if (msg.status == SendStatus.Sending) msg.uploadProgress ?: 0f else (state as? MediaState.Loading)?.progress ?: 0f
                CircularProgressIndicator(progress = { p }, modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (local != null) Icons.AutoMirrored.Rounded.InsertDriveFile else Icons.Rounded.Download,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(file.name, color = color, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(11.dp), tint = color.copy(alpha = 0.6f))
                Spacer(Modifier.width(3.dp))
                Text(formatSize(file.size), color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

fun openFile(context: android.content.Context, local: java.io.File, file: FileRef) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        val named = java.io.File(dir, file.name.replace("/", "_"))
        local.copyTo(named, overwrite = true)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", named)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, file.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        android.widget.Toast.makeText(context, "Нет приложения, чтобы открыть файл", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun previewOf(type: String, text: String, file: FileRef?): String = when (type) {
    "image" -> "Фото" + if (text.isNotBlank()) " · $text" else ""
    "video" -> if (file?.square == true) "Видеосообщение" else "Видео" + if (text.isNotBlank()) " · $text" else ""
    "file" -> ""+(file?.name ?: "Файл")
    "voice" -> "Голосовое сообщение"
    "square" -> "Видеосообщение"
    "sticker" -> "Стикер"
    "gift" -> "Подарок"
    "call" -> "Звонок"
    else -> text
}

private val STICKER_LINK = Regex("ryzik://stickers/([A-Za-z0-9-]+)")

/** Сообщение только из 1–3 эмодзи показываем крупно, как в Telegram. */
fun isEmojiOnly(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 24) return false
    var count = 0
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        val type = Character.getType(cp)
        val isEmojiPart = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp == 0x200D || cp in 0xFE00..0xFE0F ||
            cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F || type == Character.OTHER_SYMBOL.toInt() || cp == 0x20E3
        if (!isEmojiPart) return false
        if (cp != 0x200D && cp !in 0xFE00..0xFE0F && cp !in 0x1F3FB..0x1F3FF) count++
        i += Character.charCount(cp)
    }
    return count in 1..3
}

/** Подарок в чате: картинка, название и номер экземпляра. */
@Composable
private fun GiftContent(g: app.ryzik.chat.data.GiftRef, mine: Boolean, onBubble: Color) {
    Column(
        Modifier.padding(10.dp).widthIn(min = 180.dp, max = 220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        app.ryzik.chat.ui.flux.GiftImage(g, Modifier.size(140.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (mine) "Вы подарили" else "Подарок для вас",
            style = MaterialTheme.typography.labelMedium,
            color = onBubble.copy(alpha = 0.7f),
        )
        Text(
            g.title + if (g.serial > 0) " #${g.serial}" else "",
            style = MaterialTheme.typography.titleMedium,
            color = onBubble,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (g.price > 0) {
            Spacer(Modifier.height(2.dp))
            app.ryzik.chat.ui.flux.FluxAmount(g.price, color = onBubble, iconSize = 14.dp, bold = false)
        }
    }
}

/** Запись о звонке: значок, тип звонка и длительность или исход. */
@Composable
private fun CallContent(call: app.ryzik.chat.data.CallRef, mine: Boolean, onBubble: Color) {
    val bad = call.status != "ok"
    val color = if (bad) MaterialTheme.colorScheme.error else Color(0xFF2FBF71)
    val title = when {
        call.status == "ok" -> if (mine) "Исходящий" else "Входящий"
        call.status == "missed" && !mine -> "Пропущенный"
        call.status == "declined" -> if (mine) "Отклонён собеседником" else "Отклонённый"
        call.status == "busy" -> "Занято"
        call.status == "forbidden" -> "Звонки ограничены"
        call.status == "cancelled" -> if (mine) "Отменённый" else "Пропущенный"
        call.status == "missed" -> "Без ответа"
        else -> "Не удалось соединиться"
    }
    Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    call.video -> Icons.Rounded.Videocam
                    bad -> Icons.Rounded.PhoneMissed
                    mine -> Icons.Rounded.CallMade
                    else -> Icons.Rounded.CallReceived
                },
                null, tint = color, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text((if (call.video) "Видеозвонок" else "Звонок"), style = MaterialTheme.typography.titleSmall, color = onBubble)
            Text(
                title + if (call.status == "ok") " · ${app.ryzik.chat.data.callDuration(call.duration)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = onBubble.copy(alpha = 0.7f),
            )
        }
    }
}
