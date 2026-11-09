package androidx.activity.compose

import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

class ManagedActivityResultLauncher<I, O>(private val contract: ActivityResultContract<I, O>, private val onResult: () -> (O) -> Unit) {
    fun launch(input: I) {
        val r = contract.run(input)
        onResult()(r)
    }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ManagedActivityResultLauncher<I, O> {
    val cb = rememberUpdatedState(onResult)
    return remember(contract::class) { ManagedActivityResultLauncher(contract) { cb.value } }
}

/** «Назад» на ПК — клавиша Esc. Обработчики складываются в стек, срабатывает последний включённый. */
object BackDispatcher {
    class Entry(var enabled: Boolean, var action: () -> Unit)
    val entries = mutableListOf<Entry>()
    /** true, если нажатие обработано. */
    fun back(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.action()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val entry = remember { BackDispatcher.Entry(enabled, onBack) }
    entry.enabled = enabled
    entry.action = onBack
    DisposableEffect(entry) {
        BackDispatcher.entries.add(entry)
        onDispose { BackDispatcher.entries.remove(entry) }
    }
}
