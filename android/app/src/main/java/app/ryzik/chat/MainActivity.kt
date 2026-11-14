package app.ryzik.chat

import app.ryzik.chat.ui.flux.FluxScreen
import app.ryzik.chat.ui.flux.ConfettiOverlay
import app.ryzik.chat.ui.chat.StickerViewer
import app.ryzik.chat.ui.chat.StickerPackDialog
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.ryzik.chat.ui.premium.AppIcons
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.ryzik.chat.call.CallPhase
import app.ryzik.chat.data.AppSettings
import app.ryzik.chat.ui.call.CallScreen
import app.ryzik.chat.ui.channel.InviteDialog
import app.ryzik.chat.ui.channel.NewChannelScreen
import app.ryzik.chat.ui.channel.parseInviteCode
import app.ryzik.chat.ui.premium.PremiumScreen
import app.ryzik.chat.ui.premium.ProfileLookScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.SeasonStyle
import app.ryzik.chat.notify.Notifier
import app.ryzik.chat.ui.admin.AdminScreen
import app.ryzik.chat.ui.auth.AuthScreen
import app.ryzik.chat.ui.auth.TermsScreen
import app.ryzik.chat.ui.chat.ChatScreen
import app.ryzik.chat.ui.chat.CommentsScreen
import app.ryzik.chat.ui.chat.MediaViewer
import app.ryzik.chat.ui.chats.ChatListScreen
import app.ryzik.chat.ui.contacts.ContactsScreen
import app.ryzik.chat.ui.home.HomeScreen
import app.ryzik.chat.ui.home.HomeTab
import androidx.compose.runtime.setValue
import app.ryzik.chat.ui.newchat.NewChatScreen
import app.ryzik.chat.ui.profile.ChatInfoScreen
import app.ryzik.chat.ui.profile.ProfileScreen
import app.ryzik.chat.ui.settings.SettingsScreen
import app.ryzik.chat.ui.settings.SettingsSection
import app.ryzik.chat.ui.settings.SettingsSectionScreen
import app.ryzik.chat.ui.theme.RyzikTheme
import app.ryzik.chat.ui.theme.SeasonalOverlay
import app.ryzik.chat.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    private val pendingChat = mutableStateOf<String?>(null)
    private val pendingInvite = mutableStateOf<String?>(null)

    private fun handleInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "ryzik" && data.host == "stickers") {
            data.lastPathSegment?.let { StickerViewer.open(it) }
            return
        }
        if (data.scheme == "ryzik" && (data.host == "join" || data.host == "c")) {
            parseInviteCode(data.toString())?.let { pendingInvite.value = it }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingChat.value = intent?.getStringExtra(EXTRA_CHAT_ID)
        handleInvite(intent)
        handleAccount(intent)
        handleCallAction(intent)
        val app = application as RyzikApp
        setContent {
            val settings by app.prefs.settings.collectAsState(initial = null)
            val auth by app.repo.auth.collectAsState()
            val adding by app.repo.addingAccount.collectAsState()
            val season by app.repo.season.collectAsState()
            val s = settings ?: AppSettings()
            val seasonStyle = if (s.seasonalTheme) season.style else SeasonStyle.None
            RyzikTheme(s, seasonStyle) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    if (settings == null || auth is AuthState.Loading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        AnimatedContent(
                            targetState = auth is AuthState.LoggedIn,
                            label = "root",
                            transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f)) },
                        ) { loggedIn ->
                            if (loggedIn) MainNav(pendingChat.value) { pendingChat.value = null }
                            else OnboardingNav(adding)
                        }
                        // Зимний снег (или другой сезон) поверх интерфейса. Отключается в настройках и при выключенных анимациях.
                        SeasonalOverlay(seasonStyle, s.animations)
                        val invite = pendingInvite.value
                        if (invite != null && auth is AuthState.LoggedIn) {
                            InviteDialog(
                                invite,
                                onDismiss = { pendingInvite.value = null },
                                onOpenChat = { id -> pendingInvite.value = null; pendingChat.value = id },
                            )
                        }
                        // Набор стикеров: из сообщения или по ссылке ryzik://stickers/<id>.
                        val pack by StickerViewer.packId.collectAsState()
                        if (pack != null && auth is AuthState.LoggedIn) {
                            StickerPackDialog(pack!!, onDismiss = { StickerViewer.close() })
                        }
                        // Конфетти при подарке.
                        if (auth is AuthState.LoggedIn) ConfettiOverlay()
                        // Премиум закончился — иконка приложения снова зимняя.
                        val premium = (auth as? AuthState.LoggedIn)?.me?.isPremium
                        LaunchedEffect(premium) { if (premium != null) AppIcons.enforce(this@MainActivity, premium) }
                        LaunchedEffect(Unit) { AppIcons.syncAfterUpdate(this@MainActivity) }
                        // Звонок открывается поверх любого экрана.
                        val call by app.calls.state.collectAsState()
                        AnimatedVisibility(
                            visible = call.phase != CallPhase.Idle,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) { CallScreen() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CHAT_ID)?.let { pendingChat.value = it }
        handleInvite(intent)
        handleAccount(intent)
        handleCallAction(intent)
    }

    /** Уведомление пришло для другого вашего аккаунта: переключаемся на него. */
    private fun handleAccount(intent: Intent?) {
        val id = intent?.getStringExtra(Notifier.EXTRA_ACCOUNT_ID) ?: return
        intent.removeExtra(Notifier.EXTRA_ACCOUNT_ID)
        val app = application as RyzikApp
        if (app.repo.myId == id) return
        val acc = app.repo.accounts.value.firstOrNull { it.userId == id } ?: return
        if (intent.getStringExtra(EXTRA_CALL_ACTION) == "accept") {
            intent.removeExtra(EXTRA_CALL_ACTION)
            val mic = androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (mic) app.calls.autoAcceptUntil = System.currentTimeMillis() + 30_000
        }
        Notifier.cancelCall(this)
        app.repo.switchAccount(acc)
    }

    /** «Ответить» в уведомлении о звонке. Без доступа к микрофону просто открываем экран звонка. */
    private fun handleCallAction(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_CALL_ACTION) != "accept") return
        intent.removeExtra(EXTRA_CALL_ACTION)
        val app = application as RyzikApp
        app.calls.state.value.let { st ->
            val needCam = st.video
            val ok = androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                (!needCam || androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            if (ok) app.calls.accept()
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CALL_ACTION = "call_action"
    }
}

private const val ANIM = 380

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

@Composable
private fun OnboardingNav(adding: Boolean) {
    val nav = rememberNavController()
    val repo = RyzikApp.instance.repo
    // Добавление второго аккаунта: сразу экран входа, «назад» возвращает в текущий аккаунт.
    androidx.activity.compose.BackHandler(enabled = adding && nav.currentBackStackEntryAsState().value?.destination?.route == "auth") {
        repo.cancelAddAccount()
    }
    NavHost(
        nav,
        startDestination = if (adding) "auth" else "welcome",
        enterTransition = { slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(ANIM)) },
        exitTransition = { slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(ANIM)) },
        popEnterTransition = { slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(ANIM)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(ANIM)) },
    ) {
        composable("welcome") { WelcomeScreen(onLogin = { nav.go("auth") }) }
        composable("auth") {
            AuthScreen(
                onBack = { if (adding) repo.cancelAddAccount() else nav.popBackStack() },
                onOpenTerms = { nav.go("terms") },
            )
        }
        composable("terms") { TermsScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainNav(pendingChat: String?, onPendingHandled: () -> Unit) {
    val nav = rememberNavController()
    val repo = RyzikApp.instance.repo
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(HomeTab.Chats) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(pendingChat) {
        if (pendingChat != null) {
            nav.go("chat/$pendingChat")
            onPendingHandled()
        }
    }

    app.ryzik.chat.update.UpdatePrompt()

    NavHost(
        nav,
        startDestination = "chats",
        enterTransition = { slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it } },
        exitTransition = { slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(ANIM)) },
        popEnterTransition = { slideInHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(ANIM)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM, easing = FastOutSlowInEasing)) { it } },
    ) {
        composable("chats") {
            HomeScreen(tab, onTab = { tab = it }) { t ->
                when (t) {
                    HomeTab.Chats -> ChatListScreen(
                        onOpenChat = { nav.go("chat/$it") },
                        onNewChat = { nav.go("newchat") },
                        onNewGroup = { nav.go("newgroup") },
                        onNewChannel = { nav.go("newchannel") },
                        onOpenSettings = { tab = HomeTab.Profile },
                        onOpenProfile = { nav.go("profile/$it") },
                    )
                    HomeTab.Contacts -> ContactsScreen(
                        onOpenChat = { nav.go("chat/$it") },
                        onOpenProfile = { nav.go("profile/$it") },
                    )
                    HomeTab.Profile -> ProfileScreen(
                        userId = repo.myId.orEmpty(),
                        onBack = null,
                        onOpenChat = { nav.go("chat/$it") },
                        onOpenProfileLook = { nav.go("profilelook") },
                        onOpenSaved = { repo.savedChat()?.let { nav.go("chat/${it.id}") } },
                    )
                    HomeTab.Settings -> SettingsScreen(
                        onBack = null,
                        onOpenSection = { nav.go("settings/${it.name}") },
                        onOpenAdmin = { nav.go("admin") },
                        onOpenPremium = { nav.go("premium") },
                        onOpenProfileLook = { nav.go("profilelook") },
                        onOpenSaved = { repo.savedChat()?.let { nav.go("chat/${it.id}") } },
                        onOpenProfile = { tab = HomeTab.Profile },
                        onOpenFlux = { nav.go("flux") },
                    )
                }
            }
        }
        composable("chat/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            ChatScreen(
                chatId = id,
                onBack = { nav.popBackStack() },
                onOpenInfo = { nav.go("chatinfo/$id") },
                onOpenMedia = { msgId -> nav.go("viewer/$id/$msgId") },
                onOpenProfile = { nav.go("profile/$it") },
                onOpenChat = { nav.go("chat/$it") },
                onOpenComments = { msgId -> nav.go("comments/$msgId") },
            )
        }
        composable("comments/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            CommentsScreen(
                postId = id,
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.go("profile/$it") },
                onOpenChat = { nav.go("chat/$it") },
            )
        }
        composable(
            "viewer/{chat}/{msg}",
            enterTransition = { fadeIn(tween(250)) + scaleIn(initialScale = 0.9f) },
            popExitTransition = { fadeOut(tween(200)) + scaleOut(targetScale = 0.9f) },
        ) { e ->
            MediaViewer(e.arguments?.getString("chat")!!, e.arguments?.getString("msg")!!, onBack = { nav.popBackStack() })
        }
        composable("chatinfo/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            ChatInfoScreen(
                chatId = id,
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.go("profile/$it") },
                onOpenChat = { nav.go("chat/$it") },
                onAddMembers = { nav.go("addmembers/$id") },
                onLeft = { nav.popBackStack("chats", inclusive = false) },
            )
        }
        composable("newchat") {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("chats") } },
            )
        }
        composable("newgroup") {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("chats") } },
                startAsGroup = true,
            )
        }
        composable("newchannel") {
            NewChannelScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("chats") } },
            )
        }
        composable("premium") { PremiumScreen(onBack = { nav.popBackStack() }, onOpenFlux = { nav.go("flux") }) }
        composable("profilelook") { ProfileLookScreen(onBack = { nav.popBackStack() }, onOpenPremium = { nav.go("premium") }) }
        composable("addmembers/{id}") { e ->
            NewChatScreen(onBack = { nav.popBackStack() }, onOpenChat = {}, addToChatId = e.arguments?.getString("id"))
        }
        composable("profile/{id}") { e ->
            ProfileScreen(
                userId = e.arguments?.getString("id")!!,
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("chats") } },
                onOpenProfileLook = { nav.go("profilelook") },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenSection = { nav.go("settings/${it.name}") },
                onOpenAdmin = { nav.go("admin") },
                onOpenPremium = { nav.go("premium") },
                onOpenProfileLook = { nav.go("profilelook") },
                onOpenSaved = { repo.savedChat()?.let { nav.go("chat/${it.id}") } },
                onOpenFlux = { nav.go("flux") },
            )
        }
        composable("flux") { FluxScreen(onBack = { nav.popBackStack() }, onOpenPremium = { nav.go("premium") }) }
        composable("settings/{section}") { e ->
            val section = SettingsSection.valueOf(e.arguments?.getString("section")!!)
            SettingsSectionScreen(section, onBack = { nav.popBackStack() }, onOpenTerms = { nav.go("terms") }, onOpenPremium = { nav.go("premium") })
        }
        composable("terms") { TermsScreen(onBack = { nav.popBackStack() }) }
        composable("admin") { AdminScreen(onBack = { nav.popBackStack() }, onOpenProfile = { nav.go("profile/$it") }) }
    }
}
