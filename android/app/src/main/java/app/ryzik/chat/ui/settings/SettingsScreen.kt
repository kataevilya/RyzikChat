package app.ryzik.chat.ui.settings

import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.PersonAdd
import app.ryzik.chat.ui.theme.PREMIUM_SEEDS_FROM
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Code
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import app.ryzik.chat.update.UpdateState
import app.ryzik.chat.update.Updater
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.BuildConfig
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.SessionInfo
import app.ryzik.chat.data.ThemeMode
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.chat.QuickReactions
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.BadgeChip
import app.ryzik.chat.ui.components.formatListTime
import app.ryzik.chat.ui.components.formatSize
import app.ryzik.chat.ui.theme.SeedColors
import app.ryzik.chat.ui.theme.WallpaperNames
import app.ryzik.chat.ui.theme.wallpaperColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector, val color: Color) {
    Appearance("Оформление", "Тема, цвета, обои, размер текста", Icons.Rounded.Palette, SettingsColors.Purple),
    Chats("Чаты", "Отправка, свайпы, реакции, эмодзи", Icons.AutoMirrored.Rounded.Chat, SettingsColors.Blue),
    Notifications("Уведомления", "Сообщения, звонки, работа в фоне", Icons.Rounded.Notifications, SettingsColors.Red),
    Privacy("Конфиденциальность", "Шифрование, сеансы, пароль", Icons.Rounded.Security, SettingsColors.Green),
    Data("Данные и память", "Автозагрузка, кэш", Icons.Rounded.Storage, SettingsColors.Teal),
    Server("Сервер", "Основной сервер RyzikChat", Icons.Rounded.Dns, SettingsColors.Orange),
    Ai("AI-помощник", "Ключ, модель, стиль и сокращение текста", Icons.Rounded.AutoAwesome, SettingsColors.Pink),
    Api("Открытый API", "Для своих приложений и других устройств", Icons.Rounded.Code, SettingsColors.Slate),
    Updates("Обновления", "Новые версии приходят прямо в приложение", Icons.Rounded.SystemUpdate, SettingsColors.Cyan),
    About("О приложении", "Версия, правила", Icons.Rounded.Info, SettingsColors.Gray),
}

/** Пункт главного экрана настроек: для сетки, списка и поиска. */
private class SettingsEntry(val key: String, val icon: ImageVector, val color: Color, val title: String, val subtitle: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenProfileLook: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenFlux: () -> Unit = {},
) {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    val accounts by repo.accounts.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var accountsOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                runCatching { repo.updateProfile(avatarFileId = repo.uploadAvatar(uri)) }
                uploading = false
            }
        }
    }

    fun section(s: SettingsSection) = SettingsEntry(s.name, s.icon, s.color, s.title, s.subtitle) { onOpenSection(s) }
    val tiles = listOf(
        section(SettingsSection.Appearance),
        section(SettingsSection.Chats),
        section(SettingsSection.Notifications),
        section(SettingsSection.Privacy),
        SettingsEntry("saved", Icons.Rounded.Bookmark, SettingsColors.Yellow, "Избранное", "Сохранённые сообщения", onOpenSaved),
        SettingsEntry("look", Icons.Rounded.AutoAwesome, SettingsColors.Pink, "Профиль", "Статус, цвет шапки, рамка", onOpenProfileLook),
    )
    val rows = buildList {
        if (me.isAdmin) add(SettingsEntry("admin", Icons.Rounded.AdminPanelSettings, SettingsColors.Indigo, "Админ-панель", "Модерация, FLUX и NFT", onOpenAdmin))
        add(section(SettingsSection.Ai))
        add(section(SettingsSection.Data))
        add(section(SettingsSection.Updates))
        add(section(SettingsSection.Api))
        add(section(SettingsSection.About))
    }
    val premiumEntry = SettingsEntry("premium", Icons.Rounded.Star, SettingsColors.Purple, "RyzikChat Премиум", if (me.isPremium) "Активен" else "Звезда у имени, файлы до 2 ГБ и не только", onOpenPremium)
    val fluxEntry = SettingsEntry("flux", Icons.Rounded.Bolt, SettingsColors.Orange, "FLUX", "Баланс: ${app.ryzik.chat.ui.flux.formatFlux(me.flux)}. Подарки и Премиум", onOpenFlux)
    val found = if (query.isBlank()) emptyList() else (tiles + premiumEntry + fluxEntry + rows).filter {
        it.title.contains(query.trim(), ignoreCase = true) || it.subtitle.contains(query.trim(), ignoreCase = true)
    }

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        app.ryzik.chat.ui.chats.RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onClick = onBack)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text("Настройки", style = MaterialTheme.typography.headlineMedium)
                }
                app.ryzik.chat.ui.chats.SearchPill(
                    query = query,
                    onQuery = { query = it },
                    placeholder = "Поиск настроек",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (query.isNotBlank()) {
                items(found.size, key = { "f_" + found[it].key }) { i ->
                    val e = found[i]
                    Box(Modifier.animateItem()) { SettingsCard(e.icon, e.color, e.title, e.subtitle, e.onClick) }
                }
                if (found.isEmpty()) item(key = "nothing") {
                    Text("Ничего не нашлось", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
                return@LazyColumn
            }
            item(key = "profile") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.clickable(onClick = onOpenProfile).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(app.ryzik.chat.ui.components.AvatarShape).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Avatar(me.displayName, repo.avatarUrl(me.avatarFileId), 64.dp)
                            if (uploading) Box(Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(me.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                if (me.isPremium) {
                                    Spacer(Modifier.width(4.dp))
                                    if (!me.emojiStatus.isNullOrBlank()) app.ryzik.chat.ui.premium.EmojiStatus(me.emojiStatus!!, 20.sp)
                                    else app.ryzik.chat.ui.components.PremiumStar(20.dp)
                                }
                            }
                            Text("@${me.username}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { name = me.displayName; bio = me.bio; editing = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Edit, "Изменить", Modifier.size(20.dp)) }
                    }
                }
            }
            item(key = "accounts") {
                val arrow by androidx.compose.animation.core.animateFloatAsState(if (accountsOpen) 180f else 0f, label = "arrow")
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateContentSize(),
                ) {
                    Column {
                        Row(Modifier.clickable { accountsOpen = !accountsOpen }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundIcon(Icons.Rounded.SwitchAccount, SettingsColors.Blue, 42.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Аккаунты", style = MaterialTheme.typography.titleMedium)
                                Text("${accounts.size} из ${app.ryzik.chat.data.MAX_ACCOUNTS} · переключение в одно касание", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.graphicsLayer { rotationZ = arrow }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (accountsOpen) {
                            accounts.forEach { acc ->
                                val current = acc.userId == me.id
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !current) { repo.switchAccount(acc) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(acc.displayName.ifBlank { acc.username }, repo.avatarUrl(acc.avatarFileId)?.takeIf { current }, 42.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(acc.displayName.ifBlank { acc.username }, style = MaterialTheme.typography.bodyLarge)
                                        Text("@${acc.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (current) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (accounts.size < app.ryzik.chat.data.MAX_ACCOUNTS) Row(
                                Modifier.fillMaxWidth().clickable { repo.beginAddAccount() }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(Modifier.width(14.dp))
                                Text("Добавить аккаунт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            item(key = "grid") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { e -> SettingsTile(e.icon, e.color, e.title, e.subtitle, e.onClick, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "premium") {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(app.ryzik.chat.ui.components.PremiumGradient))
                        .clickable(onClick = onOpenPremium)
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Star, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(premiumEntry.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(premiumEntry.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White)
                    }
                }
            }
            item(key = "flux") {
                SettingsCard(fluxEntry.icon, fluxEntry.color, "FLUX", "Подарки, NFT и Премиум за FLUX", onOpenFlux) {
                    app.ryzik.chat.ui.flux.FluxAmount(me.flux)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(rows.size, key = { "r_" + rows[it].key }) { i ->
                val e = rows[i]
                val trailing: (@Composable () -> Unit)? = if (e.key == SettingsSection.Updates.name) {
                    { UpdateBadge() }
                } else null
                SettingsCard(e.icon, e.color, e.title, e.subtitle, e.onClick, trailing)
            }
            item(key = "logout") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.clickable { confirmLogout = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon(Icons.AutoMirrored.Rounded.Logout, MaterialTheme.colorScheme.error, 42.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Выйти", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    "RyzikChat ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Профиль") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(64) }, label = { Text("Имя") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(bio, { bio = it.take(300) }, label = { Text("О себе") }, maxLines = 4, shape = RoundedCornerShape(16.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    scope.launch { runCatching { repo.updateProfile(displayName = name.trim(), bio = bio.trim()) } }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text(if (accounts.size > 1) "Аккаунт уберётся с телефона, откроется другой ваш аккаунт. Чтобы вернуться, понадобится пароль." else "Чтобы снова увидеть переписку, понадобится ваш пароль.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; repo.logout() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }
}

/** Точка «есть новая версия» у пункта «Обновления». */
@Composable
private fun UpdateBadge() {
    val state by Updater.state.collectAsState()
    if (state is UpdateState.Available || state is UpdateState.Ready) {
        Text(
            "Новая",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    } else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ===================== Разделы =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(section: SettingsSection, onBack: () -> Unit, onOpenTerms: () -> Unit, onOpenPremium: () -> Unit = {}) {
    val app = RyzikApp.instance
    val settings by app.prefs.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    fun update(block: (AppSettings) -> AppSettings) {
        scope.launch { app.prefs.update(block) }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(section.title) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (section) {
                SettingsSection.Appearance -> AppearanceSettings(settings, ::update, onOpenPremium)
                SettingsSection.Chats -> ChatSettings(settings, ::update)
                SettingsSection.Notifications -> NotificationSettings(settings, ::update)
                SettingsSection.Privacy -> PrivacySettings(settings, ::update)
                SettingsSection.Data -> DataSettings(settings, ::update)
                SettingsSection.Server -> ServerSettings()
                SettingsSection.Ai -> AiSettings(settings, ::update)
                SettingsSection.Api -> ApiSettings()
                SettingsSection.Updates -> UpdatesSettings()
                SettingsSection.About -> AboutSettings(onOpenTerms)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun AppearanceSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, onOpenPremium: () -> Unit) {
    val auth by RyzikApp.instance.repo.auth.collectAsState()
    val premium = (auth as? AuthState.LoggedIn)?.me?.isPremium == true
    Header("Тема")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ThemeMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = s.themeMode == m,
                onClick = { update { it.copy(themeMode = m) } },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
            ) { Text(m.title) }
        }
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    SwitchRow(
        "Material You",
        if (dynamicAvailable) "Цвета подстраиваются под обои телефона" else "Нужен Android 12 или новее",
        s.dynamicColor && dynamicAvailable,
        enabled = dynamicAvailable,
    ) { v -> update { it.copy(dynamicColor = v) } }
    AnimatedVisibility(!s.dynamicColor || !dynamicAvailable) {
        Column {
            Header("Цвет приложения")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(SeedColors) { i, (color, name) ->
                    val selected = s.seedColor == i
                    val size by animateDpAsState(if (selected) 56.dp else 48.dp, spring(Spring.DampingRatioMediumBouncy), label = "seed")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(size)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
                                    .clickable {
                                        if (i >= PREMIUM_SEEDS_FROM && !premium) onOpenPremium() else update { it.copy(seedColor = i) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                                else if (i >= PREMIUM_SEEDS_FROM) Icon(if (premium) Icons.Rounded.Star else Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    SwitchRow("Чёрная тема (AMOLED)", "Глубокий чёрный в тёмной теме", s.amoled) { v -> update { it.copy(amoled = v) } }

    val season by RyzikApp.instance.repo.season.collectAsState()
    SwitchRow(
        "Сезонное оформление",
        "Сейчас: ${season.style.title}. Снег и сезонные обои; выключается только у вас",
        s.seasonalTheme,
    ) { v -> update { it.copy(seasonalTheme = v) } }
    Text(
        "Сезон для всех выбирает администратор в админ-панели. Здесь вы можете отключить его только для себя — у других он останется.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
    )

    Header("Обои чата по умолчанию")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(WallpaperNames) { i, name ->
            val selected = s.wallpaper == i
            val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "wb")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = 110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, border, RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(wallpaperColors(i).let { if (it.size < 2) it + it else it }))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (i == 0) 0.2f else 0f))
                        .clickable { update { it.copy(wallpaper = i) } },
                )
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    Header("Размер текста: ${s.textSize.toInt()}")
    Slider(
        value = s.textSize, onValueChange = { v -> update { it.copy(textSize = v) } },
        valueRange = 12f..24f, steps = 11,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Header("Скругление сообщений: ${s.bubbleRadius.toInt()}")
    Slider(
        value = s.bubbleRadius, onValueChange = { v -> update { it.copy(bubbleRadius = v) } },
        valueRange = 4f..28f,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    // Предпросмотр
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(wallpaperColors(s.wallpaper)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Surface(shape = RoundedCornerShape(s.bubbleRadius.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text("Привет! Как тебе новая тема?", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = s.textSize.sp)
            }
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(s.bubbleRadius.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.align(Alignment.End)) {
                Text("Отлично смотрится", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = s.textSize.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
    SwitchRow("Компактный список чатов", null, s.compactList) { v -> update { it.copy(compactList = v) } }
    SwitchRow("Анимации", "Пружинки, появление сообщений и прочая красота", s.animations) { v -> update { it.copy(animations = v) } }
}

@Composable
private fun AiSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var url by remember(s.aiUrl) { mutableStateOf(s.aiUrl) }
    var key by remember(s.aiKey) { mutableStateOf(s.aiKey) }
    var model by remember(s.aiModel) { mutableStateOf(s.aiModel) }
    Header("Подключение")
    Text(
        "Любой OpenAI-совместимый API. Кнопка AI появляется в поле ввода, когда текста становится три строки.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Ссылка на API") },
        placeholder = { Text("https://api.example.com/v1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("API-ключ") },
        placeholder = { Text("sk-…") },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text("Модель") },
        placeholder = { Text("gpt-4o-mini") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
    val endpoint = app.ryzik.chat.data.AiClient.endpoint(url)
    if (endpoint.isNotBlank()) Text(
        "Запросы пойдут на $endpoint",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
        if (s.aiKey.isNotBlank()) TextButton(onClick = { key = ""; update { it.copy(aiKey = "") } }) { Text("Удалить ключ") }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Button(
            enabled = url.isNotBlank() && key.isNotBlank(),
            onClick = { update { it.copy(aiUrl = url.trim(), aiKey = key.trim(), aiModel = model.trim().ifBlank { "gpt-4o-mini" }) } },
        ) { Text("Сохранить") }
    }
    Header("Что умеет")
    Text("✨ Стиль текста — орфография, пунктуация и аккуратные формулировки.\n😊 Добавить эмодзи — подходящие смайлики по смыслу.\n✂️ Сократить текст — короче, без потери смысла.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
    )
    Text(
        "Ключ хранится только на этом устройстве и не отправляется на сервер RyzikChat. Текст уходит напрямую на указанный вами AI-сервис только после нажатия кнопки.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
    )
}

@Composable
private fun ChatSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Header("Отправка")
    SwitchRow("Отправка по Enter", "Новая строка — через Shift+Enter на физической клавиатуре", s.sendByEnter) { v -> update { it.copy(sendByEnter = v) } }
    SwitchRow("Свайп для ответа", "Потяните сообщение влево, чтобы ответить", s.swipeToReply) { v -> update { it.copy(swipeToReply = v) } }
    SwitchRow("Крупные эмодзи", "Сообщения из 1–3 эмодзи показываются большими", s.bigEmoji) { v -> update { it.copy(bigEmoji = v) } }
    SwitchRow("Свои эмодзи", "Объёмные эмодзи вместо системных. Выключение применится после перезапуска приложения", s.iosEmoji) { v -> update { it.copy(iosEmoji = v) } }
    EmojiFontSection(enabled = s.iosEmoji)
    Header("Быстрая реакция (двойное касание)")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(QuickReactions) { _, e ->
            val selected = s.quickReaction == e
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { update { it.copy(quickReaction = e) } },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 24.sp) }
        }
    }
}

@Composable
private fun NotificationSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    SwitchRow("Уведомления", "Показывать новые сообщения", s.notifications) { v -> update { it.copy(notifications = v) } }
    SwitchRow("Текст в уведомлении", "Показывать содержание сообщения", s.notificationPreview, enabled = s.notifications) { v -> update { it.copy(notificationPreview = v) } }
    SwitchRow("Группы", "Уведомления из групповых чатов", s.groupNotifications, enabled = s.notifications) { v -> update { it.copy(groupNotifications = v) } }
    SwitchRow("Вибрация", null, s.vibrate, enabled = s.notifications) { v -> update { it.copy(vibrate = v) } }
    SwitchRow("Звонки", "Входящие и пропущенные звонки", s.callNotifications) { v -> update { it.copy(callNotifications = v) } }
    SwitchRow("Реакции", "Когда кто-то реагирует на ваше сообщение", s.reactionNotifications, enabled = s.notifications) { v -> update { it.copy(reactionNotifications = v) } }
    SwitchRow("Внутри приложения", "Показывать уведомления из других чатов, пока приложение открыто", s.inAppNotifications, enabled = s.notifications) { v -> update { it.copy(inAppNotifications = v) } }
    SwitchRow("Со всех аккаунтов", "Сообщения и звонки приходят на все добавленные аккаунты, а не только на открытый", s.allAccountsNotifications) { v -> update { it.copy(allAccountsNotifications = v) } }

    Header("Работа в фоне")
    SwitchRow(
        "Получать при закрытом приложении",
        "RyzikChat остаётся на связи в фоне и показывает, кто пишет и кто звонит. В шторке будет тихое уведомление «RyzikChat на связи».",
        s.backgroundConnection,
    ) { v -> update { it.copy(backgroundConnection = v) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    var ignoring by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    ListItem(
        headlineContent = { Text("Не ограничивать в фоне") },
        supportingContent = {
            Text(if (ignoring) "Батарея не мешает получать сообщения" else "Нажмите и разрешите — иначе телефон может «усыплять» RyzikChat и уведомления будут опаздывать")
        },
        leadingContent = { Icon(Icons.Rounded.BatteryChargingFull, null) },
        modifier = Modifier.clickable(enabled = !ignoring) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:" + context.packageName))
                )
            }
        },
    )
    ListItem(
        headlineContent = { Text("Системные настройки уведомлений") },
        supportingContent = { Text("Звук, всплывающие окна, значок на иконке") },
        leadingContent = { Icon(Icons.Rounded.Settings, null) },
        modifier = Modifier.clickable {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        },
    )
    Text(
        "Отключить звук для отдельного чата, группы или канала можно в его меню.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun PrivacySettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var changing by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { sessions = runCatching { repo.api.sessions() }.getOrDefault(emptyList()) }

    Header("Заблокированные")
    BlockedSection()

    Header("Сквозное шифрование")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Все сообщения, фото, видео и файлы шифруются на вашем телефоне. Сервер хранит только шифротекст.")
            Spacer(Modifier.height(12.dp))
            Text("Ваш отпечаток ключа:", style = MaterialTheme.typography.labelLarge)
            Text(repo.myFingerprint(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
    Header("Приватность")
    SwitchRow("Отчёты о прочтении", "Показывать галочки «прочитано»", s.showReadReceipts) { v -> update { it.copy(showReadReceipts = v) } }
    SwitchRow("Статус «печатает…»", "Показывать, когда собеседник набирает текст", s.showTyping) { v -> update { it.copy(showTyping = v) } }

    Header("Звонки")
    CallPrivacySection()

    Header("Платные сообщения")
    MessagePriceSection()

    Header("Активные сеансы")
    sessions.forEach { ses ->
        ListItem(
            headlineContent = { Text(ses.device.ifBlank { "Устройство" } + if (ses.current) " · это устройство" else "") },
            supportingContent = { Text("Вход: ${formatListTime(ses.createdAt)}") },
        )
    }
    if (sessions.size > 1) {
        TextButton(onClick = {
            scope.launch {
                runCatching { repo.api.terminateOtherSessions() }
                sessions = runCatching { repo.api.sessions() }.getOrDefault(sessions)
            }
        }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Завершить другие сеансы", color = MaterialTheme.colorScheme.error) }
    }
    Header("Двухэтапная проверка")
    TwoFactorSection()
    Header("Юзернейм")
    UsernameSection()
    Header("Пароль")
    ListItem(
        headlineContent = { Text("Сменить пароль") },
        supportingContent = { Text("Ключи шифрования перешифруются новым паролем") },
        modifier = Modifier.clickable { changing = true },
    )
    info?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
    Header("Удаление аккаунта")
    DeleteAccountSection()

    if (changing) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text("Новый пароль") },
            text = {
                Column {
                    OutlinedTextField(old, { old = it }, label = { Text("Текущий пароль") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(new, { new = it }, label = { Text("Новый пароль (от 8 символов)") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (new.length < 8) { err = "Минимум 8 символов"; return@TextButton }
                    scope.launch {
                        runCatching { repo.changePassword(old, new) }
                            .onSuccess { changing = false; info = "Пароль изменён. Другие сеансы завершены." }
                            .onFailure { err = it.userMessage() }
                    }
                }) { Text("Сменить") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Отмена") } },
        )
    }
}

/** Удалить аккаунт навсегда: с подтверждением паролем. */
@Composable
private fun DeleteAccountSection() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Удалить аккаунт", color = MaterialTheme.colorScheme.error) },
        supportingContent = { Text("Навсегда удалит профиль, личные чаты, ваши группы и каналы без админов") },
        leadingContent = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить аккаунт?") },
            text = {
                Column {
                    Text("Это нельзя отменить. Удалятся профиль, подарки, FLUX, личные чаты и Избранное. Ваши каналы и группы перейдут админам, а если их нет — удалятся.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Напишите УДАЛИТЬ") }, singleLine = true)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && password.isNotEmpty() && confirm.trim().equals("УДАЛИТЬ", ignoreCase = true), onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.deleteAccount(password) }
                            .onSuccess { open = false }
                            .onFailure { err = it.userMessage() }
                        busy = false
                    }
                }) { Text("Удалить навсегда", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Отмена") } },
        )
    }
}

/** Смена своего @юзернейма: нужен текущий пароль, ключи шифрования пересчитываются. */
@Composable
private fun UsernameSection() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text("@${me.username}") },
        supportingContent = { Text("Сменить юзернейм. По нему вас находят и по нему вы входите") },
        leadingContent = { Icon(Icons.Rounded.AlternateEmail, null) },
        modifier = Modifier.clickable { open = true },
    )
    done?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.primary) }
    var extras by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Дополнительные юзернеймы") },
        supportingContent = {
            Text(me.extraUsernames.takeIf { it.isNotEmpty() }?.joinToString(", ") { "@$it" } ?: "Купить ещё @имена за FLUX")
        },
        leadingContent = { Icon(Icons.Rounded.Bolt, null) },
        modifier = Modifier.clickable { extras = true },
    )
    if (extras) app.ryzik.chat.ui.flux.UsernamesSheet(onDismiss = { extras = false })
    if (open) {
        var name by remember { mutableStateOf(me.username) }
        var password by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Новый юзернейм") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { v -> name = v.removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.take(32) },
                        label = { Text("Юзернейм") }, prefix = { Text("@") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Текущий пароль") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Text(
                        "Пароль нужен, чтобы перешифровать ключи под новый юзернейм. Входить потом нужно будет с новым юзернеймом.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && name.length >= 3 && name != me.username && password.isNotEmpty(), onClick = {
                    busy = true; err = null
                    scope.launch {
                        runCatching { repo.changeUsername(name, password) }
                            .onSuccess { open = false; done = "Юзернейм изменён на @$name" }
                            .onFailure { err = if (it is app.ryzik.chat.data.ApiException && it.code == "bad_credentials") "Неверный пароль" else it.userMessage() }
                        busy = false
                    }
                }) { Text(if (busy) "Сохраняем…" else "Сменить") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Отмена") } },
        )
    }
}

/** Кто может мне звонить: все, только контакты или никто. */
@Composable
private fun CallPrivacySection() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    var err by remember { mutableStateOf<String?>(null) }
    val options = listOf("all" to "Все", "contacts" to "Мои контакты", "nobody" to "Никто")
    Text(
        when (me.callPrivacy) {
            "nobody" -> "Вам никто не может позвонить."
            "contacts" -> "Звонить вам могут только люди из ваших контактов."
            else -> "Звонить вам может любой, с кем у вас есть общий чат."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = me.callPrivacy == key,
                onClick = {
                    err = null
                    scope.launch { runCatching { repo.setCallPrivacy(key) }.onFailure { err = it.userMessage() } }
                },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
}

/** Цена сообщения в FLUX для тех, кого нет в ваших контактах. FLUX достаются вам. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessagePriceSection() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me ?: return
    var custom by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun set(price: Int) {
        err = null
        scope.launch { runCatching { repo.setMessagePrice(price) }.onFailure { err = it.userMessage() } }
    }
    Text(
        if (me.messagePrice > 0) "Люди не из ваших контактов платят ${me.messagePrice} FLUX за каждое сообщение вам. FLUX приходят на ваш баланс."
        else "Сейчас писать вам может любой бесплатно. Можно назначить цену в FLUX для тех, кого нет в ваших контактах.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        listOf(0, 10, 50, 100, 500).forEach { p ->
            FilterChip(
                selected = me.messagePrice == p,
                onClick = { set(p) },
                label = { Text(if (p == 0) "Бесплатно" else "$p FLUX") },
                shape = RoundedCornerShape(14.dp),
            )
        }
        FilterChip(
            selected = me.messagePrice !in listOf(0, 10, 50, 100, 500),
            onClick = { text = me.messagePrice.takeIf { it > 0 }?.toString().orEmpty(); custom = true },
            label = { Text(if (me.messagePrice !in listOf(0, 10, 50, 100, 500)) "${me.messagePrice} FLUX" else "Своя цена") },
            shape = RoundedCornerShape(14.dp),
        )
    }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
    if (custom) {
        AlertDialog(
            onDismissRequest = { custom = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Цена сообщения") },
            text = {
                OutlinedTextField(
                    text, { v -> text = v.filter { it.isDigit() }.take(5) },
                    label = { Text("FLUX за сообщение (до 10 000)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = text.toIntOrNull() ?: 0
                    custom = false
                    set(v.coerceIn(0, 10000))
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun DataSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val repo = RyzikApp.instance.repo
    var size by remember { mutableStateOf(repo.cacheSize()) }
    SwitchRow("Автозагрузка медиа", "Скачивать фото, небольшие видео и файлы сразу", s.autoDownload) { v -> update { it.copy(autoDownload = v) } }
    Header("Память")
    ListItem(
        headlineContent = { Text("Очистить кэш") },
        supportingContent = { Text("Сейчас занято: ${formatSize(size)}. Файлы можно будет скачать снова.") },
        leadingContent = { Icon(Icons.Rounded.Storage, null) },
        modifier = Modifier.clickable { repo.clearCache(); size = repo.cacheSize() },
    )
}

@Composable
private fun ServerSettings() {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(16.dp)) {
        Text(
            "Все работают на основном сервере RyzikChat. Адрес встроен в приложение и не меняется.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(app.ryzik.chat.BuildConfig.DEFAULT_SERVER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            scope.launch { status = if (repo.checkServer()) "Сервер отвечает" else "Сервер не отвечает" }
        }) { Text("Проверить связь") }
        status?.let { Text(it) }
    }
}

@Composable
private fun ApiSettings() {
    val repo = RyzikApp.instance.repo
    val context = androidx.compose.ui.platform.LocalContext.current
    val base = repo.api.baseUrl
    var showToken by remember { mutableStateOf(false) }
    fun open(url: String) {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Column(Modifier.padding(16.dp)) {
        Text(
            "У RyzikChat открытый API: на нём можно сделать свой клиент для ПК, веба, часов или бота. " +
                "Всё описано в спецификации OpenAPI 3, там же формат шифрования и события WebSocket.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Header("Документация")
    ListItem(
        headlineContent = { Text("Документация API") },
        supportingContent = { Text("$base/api/docs") },
        leadingContent = { Icon(Icons.Rounded.Description, null) },
        modifier = Modifier.clickable { open("$base/api/docs") },
    )
    ListItem(
        headlineContent = { Text("Спецификация OpenAPI (JSON)") },
        supportingContent = { Text("$base/api/openapi.json") },
        leadingContent = { Icon(Icons.Rounded.Code, null) },
        modifier = Modifier.clickable { open("$base/api/openapi.json") },
    )
    Header("Как подключиться")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "1. POST /api/auth/login → token",
            "2. Заголовок Authorization: Bearer <token>",
            "3. WebSocket $base/ws?token=<token> — новые сообщения, «печатает», звонки",
            "4. Личные чаты и группы шифруются на устройстве (X25519 + AES-GCM), каналы — открытым текстом",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
    }
    Header("Токен этого устройства")
    ListItem(
        headlineContent = { Text(if (showToken) (repo.api.token ?: "—") else "Показать токен") },
        supportingContent = { Text("Никому не передавайте: с ним можно читать ваши чаты от вашего имени") },
        leadingContent = { Icon(Icons.Rounded.Lock, null) },
        modifier = Modifier.clickable { showToken = !showToken },
    )
}

@Composable
private fun AboutSettings(onOpenTerms: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        app.ryzik.chat.ui.components.EmptyIcon(Icons.Rounded.Forum)
        Spacer(Modifier.height(12.dp))
        Text("RyzikChat", style = MaterialTheme.typography.headlineMedium)
        Text("Версия ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Мессенджер со сквозным шифрованием, своим сервером и дизайном Material You.", style = MaterialTheme.typography.bodyMedium)
    }
    ListItem(
        headlineContent = { Text("Правила использования") },
        leadingContent = { Icon(Icons.Rounded.Description, null) },
        modifier = Modifier.clickable(onClick = onOpenTerms),
    )
}

@Composable
private fun UpdatesSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        app.ryzik.chat.ui.components.EmptyIcon(Icons.Rounded.SystemUpdate)
        Spacer(Modifier.height(12.dp))
        Text("Версия ${Updater.currentVersionName(context)}", style = MaterialTheme.typography.titleLarge)
        Text(
            when (val st = state) {
                UpdateState.Checking -> "Проверяем…"
                UpdateState.UpToDate -> "У вас последняя версия"
                is UpdateState.Available -> "Доступна версия ${st.info.versionName}"
                is UpdateState.Downloading -> "Загрузка ${(st.progress * 100).toInt()}%"
                is UpdateState.Ready -> "Версия ${st.info.versionName} скачана"
                is UpdateState.Failed -> st.message
                UpdateState.Idle -> "Приложение само проверяет обновления при запуске"
            },
            color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        (state as? UpdateState.Downloading)?.let { d ->
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
        }
        (state as? UpdateState.Available)?.info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Text(notes, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        when (val st = state) {
            is UpdateState.Available -> androidx.compose.material3.Button(onClick = { scope.launch { Updater.download(context, st.info) } }) { Text("Скачать и установить") }
            is UpdateState.Ready -> androidx.compose.material3.Button(onClick = { Updater.install(context) }) { Text("Установить") }
            is UpdateState.Downloading, UpdateState.Checking -> {}
            else -> androidx.compose.material3.FilledTonalButton(onClick = { scope.launch { Updater.check(context, silent = false) } }) { Text("Проверить обновления") }
        }
    }
    Text(
        "Приложение само проверяет новую версию на GitHub проекта RyzikChat и скачивает её оттуда, сервер для этого не нужен. Android один раз попросит разрешить установку из этого приложения.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
