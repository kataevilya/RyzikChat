package app.ryzik.chat

import androidx.activity.compose.BackDispatcher
import androidx.activity.result.contract.DesktopFiles
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.notify.Notifier
import app.ryzik.chat.platform.Nav
import app.ryzik.chat.platform.Platform
import app.ryzik.chat.platform.ToastHost
import app.ryzik.chat.platform.rememberNav
import app.ryzik.chat.ui.admin.AdminScreen
import app.ryzik.chat.ui.auth.AuthScreen
import app.ryzik.chat.ui.auth.TermsScreen
import app.ryzik.chat.ui.channel.InviteDialog
import app.ryzik.chat.ui.channel.NewChannelScreen
import app.ryzik.chat.ui.chat.ChatScreen
import app.ryzik.chat.ui.chat.CommentsScreen
import app.ryzik.chat.ui.chat.MediaViewer
import app.ryzik.chat.ui.chat.StickerPackDialog
import app.ryzik.chat.ui.chat.StickerViewer
import app.ryzik.chat.ui.chats.ChatListScreen
import app.ryzik.chat.ui.contacts.ContactsScreen
import app.ryzik.chat.ui.flux.ConfettiOverlay
import app.ryzik.chat.ui.flux.FluxScreen
import app.ryzik.chat.ui.home.HomeScreen
import app.ryzik.chat.ui.home.HomeTab
import app.ryzik.chat.ui.newchat.NewChatScreen
import app.ryzik.chat.ui.premium.PremiumScreen
import app.ryzik.chat.ui.premium.ProfileLookScreen
import app.ryzik.chat.ui.profile.ChatInfoScreen
import app.ryzik.chat.ui.profile.ProfileScreen
import app.ryzik.chat.ui.settings.SettingsScreen
import app.ryzik.chat.ui.settings.SettingsSection
import app.ryzik.chat.ui.settings.SettingsSectionScreen
import app.ryzik.chat.ui.theme.RyzikTheme
import app.ryzik.chat.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** Чат, который надо открыть (нажали на уведомление). */
private val pendingChat = MutableStateFlow<String?>(null)

fun main() {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: if (Platform.isWindows) "DIRECT3D" else "OPENGL")
    val app = RyzikApp.start()
    val icon = runCatching {
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(AppSettings::class.java.getResourceAsStream("/icon.png")!!.readBytes()).toComposeImageBitmap())
    }.getOrNull()

    application {
        var visible by remember { mutableStateOf(true) }
        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp), position = WindowPosition(Alignment.Center))
        Notifier.onOpenChat = { id -> visible = true; if (id != null) pendingChat.value = id }

        if (Notifier.supported && icon != null) {
            Tray(
                icon = icon,
                tooltip = "RyzikChat",
                onAction = { visible = true; Notifier.lastChatId?.let { pendingChat.value = it } },
                menu = {
                    Item("Открыть RyzikChat", onClick = { visible = true })
                    Item("Выйти", onClick = ::exitApplication)
                },
            )
        }

        Window(
            onCloseRequest = { if (Notifier.supported) visible = false else exitApplication() },
            visible = visible,
            state = state,
            title = "RyzikChat",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) BackDispatcher.back() else false
            },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(820, 560)
                Platform.renderApi = runCatching { window.renderApi.name }.getOrDefault("")
                DesktopFiles.window = window
                // Перетащили файлы в окно — отправляем их в открытый чат.
                window.dropTarget = java.awt.dnd.DropTarget(window, object : java.awt.dnd.DropTargetAdapter() {
                    override fun drop(e: java.awt.dnd.DropTargetDropEvent) {
                        val chatId = app.repo.openChatId
                        if (chatId == null) { e.rejectDrop(); Platform.toast("Сначала откройте чат"); return }
                        e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                        @Suppress("UNCHECKED_CAST")
                        val files = runCatching { e.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File> }.getOrNull().orEmpty()
                        files.filter { it.isFile }.forEach { app.repo.sendFile(chatId, android.net.Uri.fromFile(it), "") }
                        e.dropComplete(files.isNotEmpty())
                    }
                })
                window.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { app.windowFocused = true; app.repo.setAppActive(true) }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) { app.windowFocused = false }
                })
            }
            LaunchedEffect(visible) { app.repo.setAppActive(visible) }
            AppRoot(app)
        }
    }
}

@Composable
private fun AppRoot(app: RyzikApp) {
    val settings by app.prefs.settings.collectAsState(initial = null)
    val auth by app.repo.auth.collectAsState()
    val adding by app.repo.addingAccount.collectAsState()
    val s = settings ?: AppSettings()
    RyzikTheme(s) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (settings == null || auth is AuthState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                AnimatedContent(
                    targetState = auth is AuthState.LoggedIn,
                    label = "root",
                    transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f)) },
                ) { loggedIn ->
                    if (loggedIn) MainNav() else OnboardingNav(adding)
                }
                val pack by StickerViewer.packId.collectAsState()
                if (pack != null && auth is AuthState.LoggedIn) {
                    StickerPackDialog(pack!!, onDismiss = { StickerViewer.close() })
                }
                if (auth is AuthState.LoggedIn) ConfettiOverlay()
            }
            ToastHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

private const val ANIM = 320

/** Вход и регистрация: по центру окна, шириной как экран телефона. */
@Composable
private fun OnboardingNav(adding: Boolean) {
    val nav = rememberNav(if (adding) "auth" else "welcome")
    val repo = RyzikApp.instance.repo
    androidx.activity.compose.BackHandler(enabled = adding && nav.current == "auth") { repo.cancelAddAccount() }
    androidx.activity.compose.BackHandler(enabled = nav.canBack) { nav.back() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 480.dp).fillMaxHeight().padding(vertical = 24.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 6.dp,
        ) {
            NavContent(nav) { route ->
                when (route) {
                    "welcome" -> WelcomeScreen(onLogin = { nav.go("auth") })
                    "auth" -> AuthScreen(
                        onBack = { if (adding) repo.cancelAddAccount() else nav.back() },
                        onOpenTerms = { nav.go("terms") },
                    )
                    "terms" -> TermsScreen(onBack = { nav.back() })
                }
            }
        }
    }
}

/** Слева — вкладки (чаты, контакты, профиль, настройки), справа — открытый чат или раздел. */
@Composable
private fun MainNav() {
    val nav = rememberNav("empty")
    val repo = RyzikApp.instance.repo
    var tab by remember { mutableStateOf(HomeTab.Chats) }
    val pending by pendingChat.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { nav.reset("chat/$it"); pendingChat.value = null }
    }
    androidx.activity.compose.BackHandler(enabled = nav.canBack) { nav.back() }

    app.ryzik.chat.update.UpdatePrompt()

    // Открыть чат из списка: правая часть начинается заново с этого чата.
    fun openChat(id: String) = nav.reset("chat/$id")
    fun openSide(route: String) = nav.reset(route)

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(400.dp).fillMaxHeight()) {
            HomeScreen(tab, onTab = { tab = it }) { t ->
                when (t) {
                    HomeTab.Chats -> ChatListScreen(
                        onOpenChat = { openChat(it) },
                        onNewChat = { openSide("newchat") },
                        onNewGroup = { openSide("newgroup") },
                        onNewChannel = { openSide("newchannel") },
                        onOpenSettings = { tab = HomeTab.Profile },
                        onOpenProfile = { openSide("profile/$it") },
                    )
                    HomeTab.Contacts -> ContactsScreen(
                        onOpenChat = { openChat(it) },
                        onOpenProfile = { openSide("profile/$it") },
                    )
                    HomeTab.Profile -> ProfileScreen(
                        userId = repo.myId.orEmpty(),
                        onBack = null,
                        onOpenChat = { openChat(it) },
                        onOpenProfileLook = { openSide("profilelook") },
                        onOpenSaved = { repo.savedChat()?.let { openChat(it.id) } },
                    )
                    HomeTab.Settings -> SettingsScreen(
                        onBack = null,
                        onOpenSection = { openSide("settings/${it.name}") },
                        onOpenAdmin = { openSide("admin") },
                        onOpenPremium = { openSide("premium") },
                        onOpenProfileLook = { openSide("profilelook") },
                        onOpenSaved = { repo.savedChat()?.let { openChat(it.id) } },
                        onOpenProfile = { tab = HomeTab.Profile },
                        onOpenFlux = { openSide("flux") },
                    )
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavContent(nav) { route -> DetailRoute(route, nav) }
        }
    }
}

@Composable
private fun DetailRoute(route: String, nav: Nav) {
    val repo = RyzikApp.instance.repo
    val parts = route.split('/')
    val arg = parts.getOrNull(1).orEmpty()
    val back: () -> Unit = { nav.back() }
    when (parts[0]) {
        "empty" -> EmptyPane()
        "chat" -> ChatScreen(
            chatId = arg,
            onBack = back,
            onOpenInfo = { nav.go("chatinfo/$arg") },
            onOpenMedia = { msgId -> nav.go("viewer/$arg/$msgId") },
            onOpenProfile = { nav.go("profile/$it") },
            onOpenChat = { nav.go("chat/$it") },
            onOpenComments = { msgId -> nav.go("comments/$msgId") },
        )
        "comments" -> CommentsScreen(
            postId = arg,
            onBack = back,
            onOpenProfile = { nav.go("profile/$it") },
            onOpenChat = { nav.go("chat/$it") },
        )
        "viewer" -> MediaViewer(arg, parts.getOrNull(2).orEmpty(), onBack = back)
        "chatinfo" -> ChatInfoScreen(
            chatId = arg,
            onBack = back,
            onOpenProfile = { nav.go("profile/$it") },
            onOpenChat = { nav.go("chat/$it") },
            onAddMembers = { nav.go("addmembers/$arg") },
            onLeft = { nav.reset("empty") },
        )
        "newchat" -> NewChatScreen(onBack = back, onOpenChat = { id -> nav.reset("chat/$id") })
        "newgroup" -> NewChatScreen(onBack = back, onOpenChat = { id -> nav.reset("chat/$id") }, startAsGroup = true)
        "newchannel" -> NewChannelScreen(onBack = back, onOpenChat = { id -> nav.reset("chat/$id") })
        "premium" -> PremiumScreen(onBack = back, onOpenFlux = { nav.go("flux") })
        "profilelook" -> ProfileLookScreen(onBack = back, onOpenPremium = { nav.go("premium") })
        "addmembers" -> NewChatScreen(onBack = back, onOpenChat = {}, addToChatId = arg)
        "profile" -> ProfileScreen(
            userId = arg,
            onBack = back,
            onOpenChat = { id -> nav.reset("chat/$id") },
            onOpenProfileLook = { nav.go("profilelook") },
        )
        "flux" -> FluxScreen(onBack = back, onOpenPremium = { nav.go("premium") })
        "settings" -> SettingsSectionScreen(SettingsSection.valueOf(arg), onBack = back, onOpenTerms = { nav.go("terms") }, onOpenPremium = { nav.go("premium") })
        "terms" -> TermsScreen(onBack = back)
        "admin" -> AdminScreen(onBack = back, onOpenProfile = { nav.go("profile/$it") })
    }
}

@Composable
private fun EmptyPane() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.padding(22.dp).size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text("Выберите чат", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Сообщения защищены сквозным шифрованием",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Смена экранов с той же анимацией, что в приложении: вперёд — сдвиг справа, назад — обратно. */
@Composable
private fun NavContent(nav: Nav, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = nav.top,
        label = "nav",
        transitionSpec = {
            if (targetState.depth >= initialState.depth && targetState.id != initialState.id && !targetState.reset) {
                (slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(ANIM))) togetherWith
                    (slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(ANIM)))
            } else if (targetState.reset) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            } else {
                (slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(ANIM))) togetherWith
                    (slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(ANIM)))
            }
        },
        contentKey = { it.id },
    ) { entry -> content(entry.route) }
}
