package app.ryzik.chat.ui.welcome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

private data class IntroPage(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val text: String)

private val pages = listOf(
    IntroPage(Icons.Rounded.WavingHand, "Привет!", "Это RyzikChat — мессенджер, в котором приятно общаться. Листай, расскажем, что тут есть."),
    IntroPage(Icons.Rounded.Bolt, "Быстро и живо", "Сообщения прилетают мгновенно, а интерфейс оживает от каждого касания: анимации, реакции, «печатает…»."),
    IntroPage(Icons.Rounded.Lock, "Только для вас", "Сквозное шифрование, как в WhatsApp: прочитать переписку можете только вы и собеседник. Даже наш сервер — нет."),
    IntroPage(Icons.Rounded.AttachFile, "Фото, видео и файлы", "Отправляйте что угодно и сохраняйте важное в «Избранное», чтобы ничего не потерять."),
    IntroPage(Icons.Rounded.Palette, "Ваш стиль", "Material You подбирает цвета под ваши обои. А особые бейджи рядом с именем выдаёт только администрация."),
)

@Composable
fun WelcomeScreen(onLogin: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(scheme.surface)) {
        AnimatedBlobs(pager)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                val last = pager.currentPage == pages.lastIndex
                val alpha by animateFloatAsState(if (last) 0f else 1f, label = "skip")
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } },
                    enabled = !last,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha },
                ) { Text("Пропустить") }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                IntroPageContent(pages[index], offset, index)
            }

            PageIndicator(pager, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoginButton(onLogin, Modifier.weight(1f))
                val last = pager.currentPage == pages.lastIndex
                val nextScale by animateFloatAsState(if (last) 0f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "next")
                if (nextScale > 0.01f) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
                            .clip(CircleShape)
                            .background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Далее", tint = scheme.onSecondaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LoginButton(onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale * (1f + glow * 0.015f)
                scaleY = scale * (1f + glow * 0.015f)
            },
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = (2 + glow * 4).dp),
    ) {
        Text("Войти", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntroPageContent(page: IntroPage, offset: Float, index: Int) {
    val abs = offset.absoluteValue.coerceIn(0f, 1f)
    val infinite = rememberInfiniteTransition(label = "float")
    val floatY by infinite.animateFloat(-10f, 10f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val wave by infinite.animateFloat(-14f, 18f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave")
    val orbit by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbit")
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(240.dp)
                .graphicsLayer {
                    val s = lerp(1f, 0.6f, abs)
                    scaleX = s; scaleY = s
                    alpha = lerp(1f, 0f, abs)
                    rotationZ = offset * 25f
                    translationY = floatY
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(scheme.primaryContainer, scheme.primaryContainer.copy(alpha = 0f)), c, r), r, c)
                drawCircle(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary), Offset(0f, 0f), Offset(size.width, size.height)), r * 0.58f, c)
                for (i in 0 until 3) {
                    val a = (orbit + i * 120f) * PI.toFloat() / 180f
                    val rr = r * (0.78f + 0.06f * i)
                    drawCircle(
                        listOf(scheme.secondary, scheme.tertiary, scheme.primary)[i].copy(alpha = 0.85f),
                        radius = r * (0.06f + 0.02f * i),
                        center = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr),
                    )
                }
            }
            Icon(
                page.icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp).graphicsLayer {
                    if (index == 0) {
                        rotationZ = wave
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.9f)
                    }
                },
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
            modifier = Modifier.graphicsLayer {
                translationX = offset * size.width * 0.5f
                alpha = 1f - abs
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                translationX = offset * size.width * 0.8f
                alpha = 1f - abs
            },
        )
    }
}

@Composable
private fun PageIndicator(pager: PagerState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pager.pageCount) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "w")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "c",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** Плавающие цветные пятна на фоне, сдвигаются вместе со страницами. */
@Composable
private fun AnimatedBlobs(pager: PagerState) {
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "blobs")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.fillMaxSize()) {
        val p = pager.currentPage + pager.currentPageOffsetFraction
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        blob(scheme.primary, w * (0.2f + 0.1f * sin(phase)) - p * w * 0.15f, h * (0.18f + 0.04f * cos(phase)), w * 0.7f)
        blob(scheme.tertiary, w * (0.9f + 0.08f * cos(phase * 2)) - p * w * 0.1f, h * (0.45f + 0.05f * sin(phase)), w * 0.6f)
        blob(scheme.secondary, w * (0.4f + 0.12f * sin(phase + 1f)) + p * w * 0.08f, h * (0.85f + 0.03f * cos(phase)), w * 0.8f)
    }
}
