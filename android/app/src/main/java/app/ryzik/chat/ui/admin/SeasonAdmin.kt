package app.ryzik.chat.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.SeasonStyle
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.launch

private fun seasonHint(style: SeasonStyle) = when (style) {
    SeasonStyle.None -> "Обычное оформление без сезона"
    SeasonStyle.Winter -> "Снег, морозные акценты и зимние обои"
    SeasonStyle.Halloween -> "Тыквенные цвета и летящие листья"
    SeasonStyle.NewYear -> "Праздничные огоньки и снег"
}

/** Админ-панель → «Сезон»: включает оформление сразу для всех пользователей. */
@Composable
fun SeasonAdmin(padding: PaddingValues) {
    val app = RyzikApp.instance
    val season by app.repo.season.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Сезонное оформление", style = MaterialTheme.typography.titleLarge)
            Text(
                "Выберите сезон — он сразу включится у всех. Пользователь может отключить его только у себя в настройках, у остальных он останется.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(SeasonStyle.entries) { style ->
            val selected = season.style == style
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { app.repo.setSeasonGlobally(style) }.onFailure { error = it.userMessage() }
                        busy = false
                    }
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(style.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(style.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            seasonHint(style),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) Icon(Icons.Rounded.Check, "Включено", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            if (busy) Text("Применяем…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Text(
                "Сейчас включено: ${season.style.title}. Снег и анимации можно выключить в «Оформлении» у себя — это не затронет других.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
