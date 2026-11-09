package app.ryzik.chat.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.MediaState
import app.ryzik.chat.ui.components.formatDay
import app.ryzik.chat.ui.components.formatTime
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MediaViewer(chatId: String, messageId: String, onBack: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val messages by repo.messagesOf(chatId).collectAsState()
    val users by repo.users.collectAsState()
    val msg = messages.firstOrNull { it.id == messageId }
    val file = msg?.content?.file
    var showUi by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (msg == null || file == null) {
            Text("Файл недоступен", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            val state by mediaStateOf(file)
            LaunchedEffect(file.id) { if (msg.localFile == null) repo.download(file) }
            val local = msg.localFile ?: (state as? MediaState.Ready)?.file
            when {
                local == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                msg.type == "video" -> VideoPlayer(local)
                else -> ZoomableImage(local) { showUi = !showUi }
            }
        }

        androidx.compose.animation.AnimatedVisibility(showUi, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(msg?.let { users[it.senderId]?.displayName } ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    msg?.let { Text("${formatDay(it.createdAt)}, ${formatTime(it.createdAt)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium) }
                }
                val ready = msg?.localFile ?: file?.let { (repo.mediaState(it.id).value as? MediaState.Ready)?.file }
                if (ready != null && file != null) {
                    IconButton(onClick = { openFile(context, ready, file) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в другом приложении", tint = Color.White)
                    }
                }
            }
        }

        val caption = msg?.content?.text.orEmpty()
        if (caption.isNotBlank()) {
            androidx.compose.animation.AnimatedVisibility(showUi, modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(
                    caption,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ZoomableImage(file: File, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scope.launch { scale.snapTo((scale.value * zoom).coerceIn(1f, 6f)) }
        offset = if (scale.value > 1f) offset + pan * scale.value else Offset.Zero
    }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scope.launch {
                            if (scale.value > 1.1f) {
                                offset = Offset.Zero
                                scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                            } else scale.animateTo(2.5f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    },
                )
            }
            .transformable(transform)
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll) { e ->
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                scope.launch { scale.snapTo((scale.value * (if (dy < 0) 1.15f else 1f / 1.15f)).coerceIn(1f, 6f)) }
                if (scale.value <= 1f) offset = Offset.Zero
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun VideoPlayer(file: File) {
    val player = remember(file) { mutableStateOf<javafx.scene.media.MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0.0) }
    var dur by remember { mutableStateOf(0.0) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(file) {
        InlinePlayer.stop()
        app.ryzik.chat.platform.FxMedia.run {
            runCatching {
                val mp = javafx.scene.media.MediaPlayer(javafx.scene.media.Media(file.toURI().toString()))
                mp.setOnReady { dur = mp.totalDuration.toMillis() }
                mp.currentTimeProperty().addListener { _, _, t -> pos = t.toMillis() }
                mp.setOnPlaying { playing = true }
                mp.setOnPaused { playing = false }
                mp.setOnEndOfMedia { playing = false; mp.pause(); mp.seek(javafx.util.Duration.ZERO) }
                mp.setOnError { failed = true }
                mp.isAutoPlay = true
                player.value = mp
            }.onFailure { failed = true }
        }
        onDispose {
            val mp = player.value
            player.value = null
            if (mp != null) app.ryzik.chat.platform.FxMedia.run { mp.stop(); mp.dispose() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val mp = player.value
        if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Это видео не открывается здесь", color = Color.White)
                androidx.compose.material3.TextButton(onClick = { app.ryzik.chat.platform.Platform.openFile(file) }) { Text("Открыть в плеере") }
            }
        } else if (mp != null) {
            app.ryzik.chat.platform.VideoSurface(
                mp,
                Modifier.fillMaxSize().pointerInput(mp) {
                    detectTapGestures(onTap = { app.ryzik.chat.platform.FxMedia.run { if (playing) mp.pause() else mp.play() } })
                },
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { app.ryzik.chat.platform.FxMedia.run { if (playing) mp.pause() else mp.play() } }) {
                    Icon(if (playing) androidx.compose.material.icons.Icons.Rounded.Pause else androidx.compose.material.icons.Icons.Rounded.PlayArrow, "Пауза", tint = Color.White)
                }
                Text(app.ryzik.chat.ui.components.formatDuration(pos.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(
                    value = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f,
                    onValueChange = { f -> app.ryzik.chat.platform.FxMedia.run { mp.seek(javafx.util.Duration.millis(dur * f)) } },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(app.ryzik.chat.ui.components.formatDuration(dur.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
