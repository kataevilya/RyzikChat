package app.ryzik.chat.platform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Стек экранов вместо navigation-compose: маршруты те же, что в приложении для телефона. */
class Nav(start: String) {
    data class Entry(val route: String, val id: Long, val depth: Int, val reset: Boolean)
    private var seq = 0L
    private val stack = mutableStateListOf(Entry(start, seq++, 0, false))

    val top: Entry get() = stack.last()
    val current: String get() = top.route
    val canBack: Boolean get() = stack.size > 1

    fun go(route: String) {
        if (current == route) return
        stack.add(Entry(route, seq++, stack.size, false))
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Заменить весь стек одним экраном (открыли другой чат из списка слева). */
    fun reset(route: String) {
        if (stack.size == 1 && current == route) return
        stack.clear()
        stack.add(Entry(route, seq++, 0, true))
    }
}

@Composable
fun rememberNav(start: String) = remember { Nav(start) }

/** Всплывающая подсказка (аналог Toast на Android). */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val toast by Platform.toastFlow.collectAsState()
    var shown by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        shown = t.second
        visible = true
        delay(2200)
        visible = false
    }
    AnimatedVisibility(visible, modifier.padding(bottom = 96.dp), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
            Text(shown.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}
