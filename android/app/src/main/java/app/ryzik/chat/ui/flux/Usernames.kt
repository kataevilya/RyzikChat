package app.ryzik.chat.ui.flux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.ApiException
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.UsernameOffer
import app.ryzik.chat.data.UsernamePrices
import app.ryzik.chat.data.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PriceLabels = listOf("3 символа", "4 символа", "5 символов", "6–7 символов", "8 и больше")

/** Карточка «Юзернеймы» на экране FLUX и в настройках. */
@Composable
fun UsernamesCard(modifier: Modifier = Modifier) {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    var open by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(Modifier.clickable { open = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(FluxGradient)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AlternateEmail, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Дополнительные юзернеймы", style = MaterialTheme.typography.titleMedium)
                Text(
                    me?.extraUsernames?.takeIf { it.isNotEmpty() }?.joinToString(", ") { "@$it" }
                        ?: "Ещё несколько @имён для профиля. Чем короче, тем дороже",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text("от", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            FluxAmount(500)
        }
    }
    if (open) UsernamesSheet(onDismiss = { open = false })
}

/** Шторка: свои дополнительные юзернеймы и покупка нового за FLUX. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernamesSheet(onDismiss: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    var query by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<UsernameOffer?>(null) }
    var checking by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<UsernameOffer?>(null) }
    var makeMain by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var prices by remember { mutableStateOf(UsernamePrices()) }
    LaunchedEffect(Unit) { runCatching { repo.api.usernamePrices() }.onSuccess { prices = it } }

    LaunchedEffect(query) {
        check = null
        if (query.length < 3) { checking = false; return@LaunchedEffect }
        checking = true
        delay(350)
        check = runCatching { repo.api.checkUsername(query) }.getOrElse { UsernameOffer(query, false, 0, it.userMessage()) }
        checking = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Юзернеймы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FluxAmount(me.flux)
            }
            Text(
                "По дополнительным юзернеймам вас находят в поиске, они видны в профиле. Входить нужно по основному.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Свои юзернеймы
            UsernameRow("@${me.username}", "Основной, по нему вы входите", main = true)
            me.extraUsernames.forEach { name ->
                UsernameRow("@$name", "Дополнительный", main = false, onMakeMain = { makeMain = name }, onRelease = { release = name })
            }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }

            // Покупка
            Text("Купить новый", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            OutlinedTextField(
                query, { v -> query = v.removePrefix("@").filter { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() || it == '_' }.take(32) },
                label = { Text("Юзернейм") }, prefix = { Text("@") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val c = check
            when {
                query.isNotEmpty() && query.length < 3 -> Text("Минимум 3 символа", color = MaterialTheme.colorScheme.onSurfaceVariant)
                checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Проверяем…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                c != null && c.available -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF2FBF71), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("@${c.username} свободен", modifier = Modifier.weight(1f))
                    Button(onClick = { buying = c }, shape = RoundedCornerShape(16.dp)) {
                        Text("Купить за ${formatFlux(c.price)}")
                    }
                }
                c != null -> Text(c.reason, color = MaterialTheme.colorScheme.error)
            }

            // Цены
            Text("Цены", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            Text(
                "Можно купить до ${prices.max}. Сейчас у вас ${me.extraUsernames.size}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PriceLabels.zip(prices.prices).forEach { (label, price) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FluxAmount(price, iconSize = 16.dp, bold = false)
                }
            }
        }
    }

    buying?.let { c ->
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) buying = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Купить @${c.username}?") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FluxAmount(c.price)
                    Text("Юзернейм появится в профиле. Его можно сделать основным или освободить, но FLUX за него не возвращаются.", style = MaterialTheme.typography.bodyMedium)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.buyUsername(c.username) }
                            .onSuccess { buying = null; query = ""; info = "@${c.username} теперь ваш"; Confetti.fire() }
                            .onFailure { error = it.userMessage() }
                        busy = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Купить")
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { buying = null }) { Text("Отмена") } },
        )
    }

    makeMain?.let { name ->
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) makeMain = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Сделать @$name основным?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Входить нужно будет с @$name, а @${me.username} останется дополнительным.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Текущий пароль") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && password.isNotEmpty(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { repo.changeUsername(name, password) }
                            .onSuccess { makeMain = null; info = "Теперь основной юзернейм — @$name" }
                            .onFailure { error = if (it is ApiException && it.code == "bad_credentials") "Неверный пароль" else it.userMessage() }
                        busy = false
                    }
                }) { Text(if (busy) "Сохраняем…" else "Сделать основным") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { makeMain = null }) { Text("Отмена") } },
        )
    }

    release?.let { name ->
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) release = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Освободить @$name?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Юзернейм пропадёт из профиля, и его сможет занять кто угодно. FLUX не вернутся.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.releaseUsername(name) }
                            .onSuccess { release = null; info = "@$name освобождён" }
                            .onFailure { error = it.userMessage() }
                        busy = false
                    }
                }) { Text("Освободить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { release = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun UsernameRow(name: String, subtitle: String, main: Boolean, onMakeMain: () -> Unit = {}, onRelease: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!main) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Действия") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Сделать основным") }, onClick = { menu = false; onMakeMain() })
                DropdownMenuItem(text = { Text("Освободить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onRelease() })
            }
        }
    }
}
