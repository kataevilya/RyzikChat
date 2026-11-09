package app.ryzik.chat.ui.flux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.FluxInfo
import app.ryzik.chat.data.GiftItem
import app.ryzik.chat.data.OwnedGift
import app.ryzik.chat.data.User
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.chats.RoundButton
import app.ryzik.chat.ui.chats.SearchPill
import app.ryzik.chat.ui.components.Avatar
import app.ryzik.chat.ui.components.formatListTime
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Цвета FLUX: от янтарного к фиолетовому. */
val FluxGradient = listOf(Color(0xFFFFB443), Color(0xFFFF6B6B), Color(0xFF9C4DFF))

/** Значок FLUX: молния в градиентном круге. */
@Composable
fun FluxIcon(size: Dp = 20.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(FluxGradient)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(size * 0.72f))
    }
}

/** Сумма с значком: «⚡ 1 000». */
@Composable
fun FluxAmount(amount: Long, color: Color = MaterialTheme.colorScheme.onSurface, iconSize: Dp = 18.dp, bold: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FluxIcon(iconSize)
        Spacer(Modifier.width(4.dp))
        Text(formatFlux(amount), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

fun formatFlux(n: Long): String = String.format(Locale("ru"), "%,d", n).replace(',', ' ')

private fun kindText(kind: String) = when (kind) {
    "admin" -> "Администрация"
    "premium" -> "Премиум"
    "gift" -> "Подарок"
    "gift_sale" -> "Продажа подарка"
    "paid_message" -> "Платное сообщение"
    "paid_message_in" -> "Входящее платное сообщение"
    "username" -> "Юзернейм"
    else -> kind
}

// ===================== Экран FLUX =====================

@Composable
fun FluxScreen(onBack: () -> Unit, onOpenPremium: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    var info by remember { mutableStateOf<FluxInfo?>(null) }
    var shop by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var buying by remember { mutableStateOf<GiftItem?>(null) }
    var market by remember { mutableStateOf<List<OwnedGift>>(emptyList()) }
    var buyingResale by remember { mutableStateOf<OwnedGift?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { repo.refreshMe() }
        runCatching { repo.api.flux() }.onSuccess { info = it }.onFailure { error = it.userMessage() }
        runCatching { repo.api.giftShop() }.onSuccess { shop = it }
        runCatching { repo.api.giftMarket() }.onSuccess { market = it }
    }
    val balance = me?.flux ?: info?.balance ?: 0

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("FLUX", style = MaterialTheme.typography.headlineMedium)
                }
                // Баланс
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(FluxGradient)).padding(24.dp),
                ) {
                    Column {
                        Text("Ваш баланс", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            Text(formatFlux(balance), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            "На FLUX покупаются подарки, юзернеймы и Премиум. Пока FLUX выдаёт администрация.",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            // Премиум
            item {
                val price = info?.premiumMonthPrice ?: 1000
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.clickable(onClick = onOpenPremium).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(app.ryzik.chat.ui.components.PremiumGradient)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Star, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Премиум на месяц", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    me?.isPremium == true && me.premiumUntil == null -> "У вас бессрочный Премиум"
                                    me?.premiumUntil != null -> "Активен до ${dateText(me.premiumUntil)}. Можно продлить"
                                    else -> "Звезда у имени, оформление профиля и не только"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FluxAmount(price)
                    }
                }
            }
            item { UsernamesCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) }
            // Витрина: подарки-эмодзи и NFT
            val emojiGifts = shop.filter { it.kind == "emoji" }
            val nfts = shop.filter { it.kind != "emoji" }
            item {
                Text("Подарки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                if (shop.isEmpty()) Text(
                    "На витрине пока пусто.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                GiftGrid(emojiGifts) { buying = it }
                if (nfts.isNotEmpty()) {
                    Text("NFT", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
                    Text(
                        "Коллекционные подарки от администрации. У каждого экземпляра свой номер, тираж может быть ограничен.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                    GiftGrid(nfts) { buying = it }
                }
                if (market.isNotEmpty()) {
                    Row(Modifier.padding(start = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storefront, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Маркет", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Подарки, которые продают другие пользователи. FLUX уходят продавцу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        market.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { g -> MarketCard(g, Modifier.weight(1f)) { buyingResale = g } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            // История
            item {
                Text("История", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
                if (info?.history.isNullOrEmpty()) Text("Операций пока не было.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(info?.history.orEmpty(), key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.note.ifBlank { kindText(t.kind) }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatListTime(t.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (t.amount > 0) "+" else "") + formatFlux(t.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (t.amount > 0) Color(0xFF2FBF71) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    buying?.let { item ->
        BuyGiftDialog(item, to = null, onDismiss = { buying = null }, onDone = { buying = null; reload++ })
    }
    buyingResale?.let { g ->
        BuyResaleDialog(g, onDismiss = { buyingResale = null }, onDone = { buyingResale = null; reload++ })
    }
}

@Composable
private fun MarketCard(g: OwnedGift, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            g.item?.let { GiftImage(it, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(6.dp))
            Text("${g.item?.title ?: "Подарок"} #${g.serial}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            g.resalePrice?.let { FluxAmount(it, iconSize = 14.dp) }
            g.owner?.let { Text("@${it.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Покупка подарка, который продаёт другой пользователь. */
@Composable
fun BuyResaleDialog(g: OwnedGift, onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val price = g.resalePrice ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Купить «${g.item?.title ?: "Подарок"}» #${g.serial}?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                g.item?.let { GiftImage(it, Modifier.size(140.dp)) }
                g.owner?.let { Text("Продаёт ${it.displayName} (@${it.username})", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FluxAmount(price)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { repo.api.buyResaleGift(g.id, price) }
                        .onSuccess { runCatching { repo.refreshMe() }; Confetti.fire(); onDone() }
                        .onFailure { error = it.userMessage() }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Купить за ${formatFlux(price)} FLUX")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Цена, за которую владелец выставляет подарок на продажу другим. */
@Composable
private fun ResalePriceDialog(g: OwnedGift, onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(g.resalePrice?.toString() ?: (g.item?.price ?: 0).toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val price = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Продать «${g.item?.title ?: "Подарок"}» #${g.serial}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Подарок появится в маркете и с ценой в вашем профиле. Когда его купят, FLUX придут вам.")
                OutlinedTextField(
                    text, { v -> text = v.filter { it.isDigit() }.take(9) },
                    label = { Text("Цена в FLUX") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && price != null && price > 0, onClick = {
                busy = true
                scope.launch {
                    runCatching { repo.api.setGiftResale(g.id, price) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.userMessage() }
                    busy = false
                }
            }) { Text("Выставить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}

fun dateText(t: Long): String = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(t))

@Composable
private fun GiftGrid(items: List<GiftItem>, onClick: (GiftItem) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g -> GiftCard(g, Modifier.weight(1f)) { onClick(g) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GiftCard(item: GiftItem, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.clickable(enabled = item.left != 0, onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GiftImage(item, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.left == 0) Text("Раскуплен", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            else {
                FluxAmount(item.price, iconSize = 14.dp)
                item.left?.let { Text("осталось $it из ${item.supply}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Покупка: себе (to = null) или в подарок. */
@Composable
fun BuyGiftDialog(item: GiftItem, to: User?, onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (to == null) "Купить «${item.title}»?" else "Подарить «${item.title}» ${to.displayName}?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GiftImage(item, Modifier.size(140.dp))
                if (item.description.isNotBlank()) Text(item.description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                FluxAmount(item.price)
                if (to != null) OutlinedTextField(message, { message = it.take(200) }, label = { Text("Подпись (необязательно)") }, shape = RoundedCornerShape(16.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { repo.buyGift(item.id, to?.id, message.trim()) }
                        .onSuccess { onDone() }
                        .onFailure { error = it.userMessage() }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (to == null) "Купить" else "Подарить")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Витрина в виде шторки — чтобы подарить подарок конкретному человеку из его профиля. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftShopSheet(to: User, onDismiss: () -> Unit, onGifted: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val auth by repo.auth.collectAsState()
    val me = (auth as? AuthState.LoggedIn)?.me
    var shop by remember { mutableStateOf<List<GiftItem>?>(null) }
    var buying by remember { mutableStateOf<GiftItem?>(null) }
    LaunchedEffect(Unit) { shop = runCatching { repo.api.giftShop() }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Подарок для ${to.displayName}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                FluxAmount(me?.flux ?: 0)
            }
            Spacer(Modifier.height(12.dp))
            when {
                shop == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shop!!.isEmpty() -> Text("На витрине пока пусто.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 480.dp),
                ) {
                    items(shop!!, key = { it.id }) { item -> GiftCard(item, Modifier) { buying = item } }
                }
            }
        }
    }
    buying?.let { item -> BuyGiftDialog(item, to, onDismiss = { buying = null }, onDone = { buying = null; onGifted() }) }
}

// ===================== Подарки в профиле =====================

/** Сетка подарков пользователя. Свои можно скрыть или подарить дальше. */
@Composable
fun ProfileGifts(userId: String, isMe: Boolean, refreshKey: Int = 0) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var gifts by remember { mutableStateOf<List<OwnedGift>>(emptyList()) }
    var open by remember { mutableStateOf<OwnedGift?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(userId, reload, refreshKey) { gifts = runCatching { repo.api.userGifts(userId) }.getOrDefault(gifts) }
    if (gifts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CardGiftcard, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Подарки · ${gifts.size}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        gifts.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { g ->
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                        Column(Modifier.clickable { open = g }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                g.item?.let { GiftImage(it, Modifier.fillMaxWidth()) }
                                if (g.hidden) Icon(Icons.Rounded.VisibilityOff, "Скрыт", Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (g.pinned) Icon(Icons.Rounded.PushPin, "Закреплён", Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("#${g.serial}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            g.resalePrice?.let { FluxAmount(it, iconSize = 12.dp) }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    open?.let { g ->
        var transfer by remember(g.id) { mutableStateOf(false) }
        var selling by remember(g.id) { mutableStateOf(false) }
        var resale by remember(g.id) { mutableStateOf(false) }
        var buyResale by remember(g.id) { mutableStateOf(false) }
        var sellError by remember(g.id) { mutableStateOf<String?>(null) }
        if (transfer) {
            TransferGiftDialog(g, onDismiss = { transfer = false }, onDone = { transfer = false; open = null; reload++ })
        } else if (resale) {
            ResalePriceDialog(g, onDismiss = { resale = false }, onDone = { resale = false; open = null; reload++ })
        } else if (buyResale) {
            BuyResaleDialog(g, onDismiss = { buyResale = false }, onDone = { buyResale = false; open = null; reload++ })
        } else if (selling) {
            AlertDialog(
                onDismissRequest = { selling = false },
                shape = RoundedCornerShape(28.dp),
                title = { Text("Продать подарок RyzikChat?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("«${g.item?.title ?: "Подарок"}» #${g.serial} исчезнет из вашего профиля, а на счёт сразу придёт ${g.sellPrice} FLUX.")
                        sellError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { repo.api.sellGift(g.id) }
                                .onSuccess { selling = false; open = null; reload++; Confetti.fire() }
                                .onFailure { sellError = it.userMessage() }
                        }
                    }) { Text("Продать за ${g.sellPrice} FLUX") }
                },
                dismissButton = { TextButton(onClick = { selling = false }) { Text("Отмена") } },
            )
        } else AlertDialog(
            onDismissRequest = { open = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("${g.item?.title ?: "Подарок"} #${g.serial}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    g.item?.let { GiftImage(it, Modifier.size(160.dp)) }
                    g.item?.description?.takeIf { it.isNotBlank() }?.let { Text(it, textAlign = TextAlign.Center) }
                    g.from?.let { Text("От ${it.displayName} (@${it.username})", color = MaterialTheme.colorScheme.primary) }
                    if (g.message.isNotBlank()) Text("«${g.message}»", textAlign = TextAlign.Center)
                    Text(dateText(g.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    g.item?.supply?.let { Text("Экземпляр ${g.serial} из $it", style = MaterialTheme.typography.labelSmall) }
                    g.resalePrice?.let { Row(verticalAlignment = Alignment.CenterVertically) { Text("Продаётся за ", style = MaterialTheme.typography.bodyMedium); FluxAmount(it, iconSize = 16.dp) } }
                    sellError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (isMe) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { repo.api.setGiftPinned(g.id, !g.pinned) }
                                    .onSuccess { open = null; reload++ }
                                    .onFailure { sellError = it.userMessage() }
                            }
                        }, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (g.pinned) "Открепить" else "Закрепить в профиле")
                        }
                        if (g.resalePrice == null) OutlinedButton(onClick = { sellError = null; resale = true }, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.Storefront, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Продать пользователям")
                        } else OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { repo.api.setGiftResale(g.id, null) }
                                    .onSuccess { open = null; reload++ }
                                    .onFailure { sellError = it.userMessage() }
                            }
                        }, shape = RoundedCornerShape(20.dp)) { Text("Снять с продажи") }
                        if (g.sellPrice > 0) OutlinedButton(onClick = { sellError = null; selling = true }, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.Sell, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Продать RyzikChat за ${g.sellPrice} FLUX")
                        }
                    } else if (g.resalePrice != null) {
                        Button(onClick = { buyResale = true }, shape = RoundedCornerShape(20.dp)) {
                            Text("Купить за ${formatFlux(g.resalePrice)} FLUX")
                        }
                    }
                }
            },
            confirmButton = {
                if (isMe) TextButton(onClick = { transfer = true }) { Text("Подарить") }
                else TextButton(onClick = { open = null }) { Text("Закрыть") }
            },
            dismissButton = {
                if (isMe) TextButton(onClick = {
                    scope.launch { runCatching { repo.api.setGiftHidden(g.id, !g.hidden) }; open = null; reload++ }
                }) {
                    Icon(if (g.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (g.hidden) "Показать в профиле" else "Скрыть")
                }
            },
        )
    }
}

/** Подарить свой подарок другому: поиск человека и подпись. */
@Composable
private fun TransferGiftDialog(g: OwnedGift, onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<User>>(emptyList()) }
    var to by remember { mutableStateOf<User?>(null) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.length < 2) { found = emptyList(); return@LaunchedEffect }
        delay(300)
        found = runCatching { repo.searchUsers(query) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Кому подарить?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val target = to
                if (target == null) {
                    SearchPill(query, { query = it }, "Имя или @username", Modifier.fillMaxWidth())
                    found.take(5).forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { to = u }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.displayName, repo.avatarUrl(u.avatarFileId), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(u.displayName, maxLines = 1)
                                Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(target.displayName, repo.avatarUrl(target.avatarFileId), 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(target.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { to = null }) { Text("Другой") }
                    }
                    OutlinedTextField(message, { message = it.take(200) }, label = { Text("Подпись (необязательно)") }, shape = RoundedCornerShape(16.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = to != null, onClick = {
                scope.launch {
                    runCatching { repo.api.transferGift(g.id, to!!.id, message.trim()) }
                        .onSuccess { onDone(); Confetti.fire() }
                        .onFailure { error = it.userMessage() }
                }
            }) { Text("Подарить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
