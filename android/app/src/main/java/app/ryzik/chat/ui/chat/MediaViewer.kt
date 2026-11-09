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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(file: File) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
        modifier = Modifier.fillMaxSize(),
    )
}
