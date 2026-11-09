package app.ryzik.chat.ui.call

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.call.CallPhase
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.formatDuration
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Полноэкранный экран звонка поверх всего приложения. */
@Composable
fun CallScreen() {
    val app = RyzikApp.instance
    val calls = app.calls
    val state by calls.state.collectAsState()
    val users by app.repo.users.collectAsState()
    val local by calls.localVideo.collectAsState()
    val remote by calls.remoteVideo.collectAsState()
    val peer = users[state.peerId]
    val name = peer?.displayName ?: "Собеседник"

    val acceptPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) calls.accept() else calls.decline()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.phase) {
        while (state.phase == CallPhase.Active) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    BackHandler(enabled = true) { if (state.phase == CallPhase.Ended) calls.dismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1035), Color(0xFF3A1C5C), Color(0xFF0E1A3A)))),
    ) {
        if (remote != null) VideoView(remote!!, calls.eglBase, Modifier.fillMaxSize(), mirror = false)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Сквозное шифрование", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(if (remote != null) 16.dp else 64.dp))
            if (remote == null) {
                PulsingAvatar(name, app.repo.avatarUrl(peer?.avatarFileId), pulsing = state.phase != CallPhase.Active && state.phase != CallPhase.Ended)
                Spacer(Modifier.height(24.dp))
            }
            Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = when (state.phase) {
                    CallPhase.Outgoing -> if (state.peerOffline) "Не в сети · звоним, пока не появится…" else "Звоним…"
                    CallPhase.Incoming -> if (state.video) "Входящий видеозвонок" else "Входящий звонок"
                    CallPhase.Connecting -> "Соединение…"
                    CallPhase.Active -> formatDuration(now - state.startedAt)
                    CallPhase.Ended -> state.endReason
                    CallPhase.Idle -> ""
                },
                label = "status",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium) }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = state.phase == CallPhase.Incoming,
                label = "controls",
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() },
            ) { incoming ->
                if (incoming) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundButton(Icons.Rounded.CallEnd, "Отклонить", Color(0xFFE53935)) { calls.decline() }
                        BouncingAccept(if (state.video) Icons.Rounded.Videocam else Icons.Rounded.Call) {
                            val perms = if (state.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                            acceptPermission.launch(perms)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ToggleButton(if (state.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (state.muted) "Микрофон выкл." else "Микрофон", state.muted) { calls.toggleMute() }
                            ToggleButton(Icons.Rounded.VolumeUp, "Динамик", state.speaker) { calls.toggleSpeaker() }
                            if (state.video) {
                                ToggleButton(if (state.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Камера", !state.cameraOn) { calls.toggleCamera() }
                                ToggleButton(Icons.Rounded.Cameraswitch, "Сменить", false) { calls.switchCamera() }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        if (state.phase == CallPhase.Ended) {
                            RoundButton(Icons.Rounded.CallEnd, "Закрыть", Color.White.copy(alpha = 0.2f)) { calls.dismiss() }
                        } else {
                            RoundButton(Icons.Rounded.CallEnd, "Завершить", Color(0xFFE53935)) { calls.hangup() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        AnimatedVisibility(
            local != null && state.cameraOn,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 48.dp, end = 16.dp),
            enter = scaleIn() + fadeIn(),
            exit = fadeOut(),
        ) {
            local?.let { track ->
                Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp) {
                    VideoView(track, calls.eglBase, Modifier.size(110.dp, 160.dp).clip(RoundedCornerShape(18.dp)), mirror = true)
                }
            }
        }
    }
}

@Composable
private fun VideoView(track: VideoTrack, egl: EglBase, modifier: Modifier, mirror: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val renderer = remember(track) {
        SurfaceViewRenderer(context).apply {
            init(egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(mirror)
            setEnableHardwareScaler(true)
        }
    }
    DisposableEffect(track) {
        track.addSink(renderer)
        onDispose {
            runCatching { track.removeSink(renderer) }
            renderer.release()
        }
    }
    AndroidView(factory = { renderer }, modifier = modifier)
}

@Composable
private fun PulsingAvatar(name: String, url: String?, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            for (i in 0 until 3) {
                val phase = (p + i / 3f) % 1f
                Box(
                    Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = 1f + phase * 0.7f
                            scaleY = 1f + phase * 0.7f
                            alpha = (1f - phase) * 0.35f
                        }
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Avatar(name, url, 130.dp)
    }
}

@Composable
private fun BouncingAccept(icon: ImageVector, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "accept")
    val y by t.animateFloat(0f, -14f, infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.graphicsLayer { translationY = y * density }) {
        RoundButton(icon, "Ответить", Color(0xFF43A047), onClick)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = color, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (active) Color(0xFF1B1035) else Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}
