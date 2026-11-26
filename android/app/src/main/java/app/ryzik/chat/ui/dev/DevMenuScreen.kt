package app.ryzik.chat.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.BuildConfig
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.ChatRepository
import app.ryzik.chat.data.User
import app.ryzik.chat.dev.DevFlags
import app.ryzik.chat.dev.DevLog
import app.ryzik.chat.ui.components.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil.imageLoader
import kotlinx.coroutines.withContext

private val DevPurple = Color(0xFF7C4DFF)

/** Скрытый экран разработчика. Открывается семью нажатиями на версию в «О приложении». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = RyzikApp.instance
    val repo = app.repo
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val connected by repo.connected.collectAsState()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    val accounts by repo.accounts.collectAsState()

    var cache by remember { mutableLongStateOf(-1L) }
    var health by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var logTick by remember { mutableIntStateOf(0) }
    var keysDialog by remember { mutableStateOf<List<String>?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(DevFlags.fpsOverlay) }
    var grid by remember { mutableStateOf(DevFlags.layoutGrid) }

    LaunchedEffect(Unit) { cache = withContext(Dispatchers.IO) { repo.cacheBytes() } }

    fun note(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }
    fun copy(text: String, msg: String) { clip(context, text); note(msg) }
    fun report() = DevReport.build(context, repo, me, connected, accounts.size, cache)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Для разработчиков") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SummaryCard(
                connected = connected,
                me = me,
                server = repo.api.baseUrl,
                cache = cache,
                accounts = accounts.size,
                fingerprint = repo.myFingerprint(),
            )

            Section("Действия")
            Group {
                DevRow(Icons.Rounded.ContentCopy, DevPurple, "Скопировать отчёт", "Версия, устройство, сервер, ключ — для переписки с поддержкой") {
                    copy(report(), "Отчёт скопирован")
                }
                RowDivider()
                DevRow(
                    Icons.Rounded.Wifi,
                    Color(0xFF00B8A9),
                    "Проверить сервер",
                    health ?: "Запрос /api/health и время ответа",
                    busy = busy == "health",
                ) {
                    busy = "health"
                    scope.launch {
                        val started = SystemClock.elapsedRealtime()
                        val ok = runCatching { repo.api.health() }.getOrDefault(false)
                        val ms = SystemClock.elapsedRealtime() - started
                        health = if (ok) "Сервер отвечает, ${ms} мс" else "Сервер не отвечает (${ms} мс)"
                        DevLog.log("сеть", health ?: "")
                        busy = null
                        note(health ?: "")
                        logTick++
                    }
                }
                RowDivider()
                DevRow(Icons.Rounded.Lock, Color(0xFF2FBF71), "Проверить ключи", "Ищем участников с битым публичным ключом") {
                    keysDialog = repo.users.value.values.distinctBy { it.id }.map { u -> keyLine(u) }
                    if (keysDialog?.isEmpty() == true) keysDialog = listOf("Пока никого не знаем: откройте чаты, чтобы приложение загрузило ключи.")
                }
                RowDivider()
                DevRow(Icons.Rounded.Key, Color(0xFF5C6BC0), "Отправить свой ключ", "POST /api/me/keys — лечим свой публичный ключ на сервере", busy = busy == "key") {
                    busy = "key"
                    scope.launch {
                        val ok = runCatching { repo.pushPublicKey() }.getOrDefault(false)
                        busy = null
                        copy(repo.myFingerprint(), if (ok) "Ключ на сервере правильный" else "Не получилось отправить ключ")
                        logTick++
                    }
                }
                RowDivider()
                DevRow(Icons.Rounded.Refresh, Color(0xFF3D8BFF), "Переподключить сокет", "Разорвать и открыть websocket заново") {
                    repo.reconnectSocket()
                    note("Переподключаемся…")
                }
                RowDivider()
                DevRow(Icons.Rounded.CleaningServices, Color(0xFF00A8E8), "Очистить кэш", "Медиа и картинки: ${if (cache < 0) "…" else formatSize(cache)}") {
                    busy = "cache"
                    scope.launch {
                        val before = withContext(Dispatchers.IO) { repo.cacheBytes() }
                        withContext(Dispatchers.IO) {
                            runCatching { repo.clearCache() }
                            runCatching { context.imageLoader.memoryCache?.clear() }
                        }
                        cache = withContext(Dispatchers.IO) { repo.cacheBytes() }
                        busy = null
                        note("Освобождено ${formatSize(before - cache)}")
                    }
                }
                RowDivider()
                DevRow(Icons.Rounded.Storage, Color(0xFFFF9F43), "Обновить данные", "refreshMe + справочники и чаты") {
                    scope.launch {
                        runCatching { repo.refreshMe() }
                        runCatching { repo.refreshChats() }
                        note("Обновлено")
                    }
                }
                RowDivider()
                DevRow(Icons.Rounded.Refresh, Color(0xFFFFB300), "Сбросить онбординг", "Приветствие покажется при следующем запуске") {
                    scope.launch { app.prefs.update { it.copy(onboardingDone = false) } }
                    note("Онбординг сброшен")
                }
                RowDivider()
                DevRow(Icons.AutoMirrored.Rounded.Send, Color(0xFFFF5A5F), "Выйти из аккаунта", "Локальные ключи и сессия удаляются") {
                    confirmLogout = true
                }
            }

            Section("Отладка интерфейса")
            Group {
                ToggleRow(Icons.Rounded.Speed, DevPurple, "Счётчик FPS", "Показывать частоту кадров поверх интерфейса", fps) {
                    fps = it
                    DevFlags.fpsOverlay = it
                }
                RowDivider()
                ToggleRow(Icons.Rounded.GridOn, Color(0xFF00B8A9), "Сетка вёрстки", "Линии каждые 8 dp поверх экрана", grid) {
                    grid = it
                    DevFlags.layoutGrid = it
                }
            }

            Section("Журнал")
            Group {
                DevRow(Icons.Rounded.Refresh, Color(0xFF3D8BFF), "Обновить", "Записей: ${DevLog.snapshot().size}", onClick = { logTick++; note("Журнал обновлён") })
                RowDivider()
                DevRow(Icons.Rounded.ContentCopy, DevPurple, "Скопировать журнал", "Связь, отправка и ключи шифрования") {
                    copy(DevLog.asText().ifBlank { "Журнал пуст" }, "Журнал скопирован")
                }
                RowDivider()
                DevRow(Icons.Rounded.Delete, Color(0xFFFF5A5F), "Очистить журнал", "Удалить все записи из памяти") {
                    DevLog.clear()
                    logTick++
                    note("Журнал очищен")
                }
            }
            LogCard(tick = logTick)

            Section("Сессия")
            Group {
                DevRow(Icons.Rounded.Lock, Color(0xFF8A8FA3), "Токен (первые 12)", repo.api.token?.take(12) ?: "—", onClick = {
                    copy(repo.api.token ?: "", "Токен скопирован")
                })
                RowDivider()
                DevRow(Icons.Rounded.BugReport, Color(0xFF5C6BC0), "Выключить режим разработчика", "Раздел исчезнет из «О приложении»") {
                    confirmDisable = true
                }
            }

            Text(
                "Режим разработчика хранится только на этом устройстве. Журнал не отправляется на сервер и очищается при закрытии приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    keysDialog?.let { lines ->
        AlertDialog(
            onDismissRequest = { keysDialog = null },
            title = { Text("Ключи участников") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items(lines) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { keysDialog = null }) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { copy(lines.joinToString("\n"), "Скопировано") }) { Text("Скопировать") } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Сессия и локальные ключи на этом телефоне будут удалены. Войти можно снова с паролем.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    repo.logout()
                    onBack()
                }) { Text("Выйти") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Выключить режим разработчика?") },
            text = { Text("Скрытый раздел закроется. Чтобы вернуть его, снова нажмите 7 раз на версию.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    DevFlags.fpsOverlay = false
                    DevFlags.layoutGrid = false
                    scope.launch {
                        app.prefs.update { it.copy(devMode = false) }
                        onBack()
                    }
                }) { Text("Выключить") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SummaryCard(connected: Boolean, me: User?, server: String, cache: Long, accounts: Int, fingerprint: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(if (connected) Color(0xFF2FBF71) else Color(0xFFFF5A5F)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connected) "Связь с сервером есть" else "Связи с сервером нет",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(10.dp))
            InfoLine("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.DEBUG) "debug" else "release"}")
            InfoLine("Устройство", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            InfoLine("Сервер", server)
            InfoLine("Аккаунт", me?.let { "@${it.username} · ${it.displayName}" } ?: "не выбран")
            InfoLine("Аккаунтов", accounts.toString())
            InfoLine("Кэш", if (cache < 0) "считаем…" else formatSize(cache))
            InfoLine("Отпечаток ключа", fingerprint.ifBlank { "—" })
            InfoLine("Аптайм", "${SystemClock.elapsedRealtime() / 1000 / 60} мин")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun DevRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (busy) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LogCard(tick: Int) {
    val entries = remember(tick) { DevLog.snapshot().take(16) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        if (entries.isEmpty()) {
            Text(
                "Пока пусто: отправьте сообщение или переподключитесь — события появятся здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB9C0D4),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column(Modifier.padding(14.dp)) {
                entries.forEach { e ->
                    Text(
                        "${DevLog.time(e.time)}  ${e.tag}  ${e.text}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCFD6E6),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun keyLine(u: User): String {
    val bytes = runCatching { app.ryzik.chat.crypto.E2E.unb64(u.publicKey) }.getOrNull()
    val size = bytes?.size ?: -1
    val status = if (size == 32) "ок" else "БИТЫЙ"
    return "${u.displayName} (@${u.username}): ${size} байт — $status"
}

/** Счётчик кадров поверх интерфейса. Включается в меню разработчика. */
@Composable
fun FpsChip(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            var frames = 0
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < 1000L) {
                withFrameNanos { }
                frames++
            }
            fps = frames
        }
    }
    Surface(
        modifier = modifier.statusBarsPadding().padding(top = 8.dp, end = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            "$fps fps",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Сетка вёрстки каждые 8 dp. Включается в меню разработчика. */
@Composable
fun LayoutGridOverlay() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Canvas(Modifier.fillMaxSize()) {
        val step = 8.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

private fun clip(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("RyzikChat", text))
    }
}

/** Текстовый отчёт для поддержки: без токена и приватных ключей. */
object DevReport {
    fun build(context: Context, repo: ChatRepository, me: User?, connected: Boolean, accounts: Int, cache: Long): String = buildString {
        appendLine("RyzikChat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}")
        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Сервер: ${repo.api.baseUrl}")
        appendLine("Связь: ${if (connected) "есть" else "нет"}")
        appendLine("Аккаунт: ${me?.let { "@${it.username} (${it.displayName})" } ?: "нет"}")
        appendLine("Аккаунтов на устройстве: $accounts")
        appendLine("Кэш: ${formatSize(cache)}")
        appendLine("Отпечаток ключа: ${repo.myFingerprint().ifBlank { "—" }}")
        appendLine("Токен: ${repo.api.token?.let { "есть, ${it.length} симв." } ?: "нет"}")
        appendLine("Битые ключи: ${repo.users.value.values.count { u -> runCatching { app.ryzik.chat.crypto.E2E.unb64(u.publicKey).size != 32 }.getOrDefault(true) }}")
        appendLine("Время: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date())}")
    }
}
