package app.ryzik.chat.ui.admin

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import app.ryzik.chat.ui.flux.GiftAnimations
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.GiftItem
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.flux.FluxAmount
import app.ryzik.chat.ui.flux.GiftImage
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Создатель NFT-подарков: картинка, название, описание, цена в FLUX и тираж.
 * Каждый купленный экземпляр получает свой номер, его можно дарить дальше.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun NftCreator(modifier: Modifier) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<GiftItem?>(null) }
    var deleting by remember { mutableStateOf<GiftItem?>(null) }

    var image by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var supply by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var animation by remember { mutableStateOf("bounce") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        runCatching { repo.api.adminGiftItems() }.onSuccess { items = it }.onFailure { error = it.userMessage() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) image = uri }
    val canCreate = image != null && title.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && !busy

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("NFT Создатель", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Загрузите картинку, добавьте надпись вроде «Доброе утро», выберите анимацию и цену в FLUX. Подарок появится на витрине в разделе NFT. Тираж ограничивает число экземпляров, у каждого свой номер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier.size(140.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null) AnimatedPreview(animation, caption) { AsyncImage(image, null, Modifier.fillMaxSize().padding(14.dp)) }
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Картинка", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(title, { title = it.take(40) }, label = { Text("Название") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(caption, { caption = it.take(40) }, label = { Text("Надпись на подарке, например «Доброе утро»") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Text("Анимация", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GiftAnimations.forEach { (key, label) ->
                            FilterChip(selected = animation == key, onClick = { animation = key }, label = { Text(label) }, shape = RoundedCornerShape(14.dp))
                        }
                    }
                    OutlinedTextField(description, { description = it.take(200) }, label = { Text("Описание (необязательно)") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            price, { v -> price = v.filter { it.isDigit() }.take(9) },
                            label = { Text("Цена, FLUX") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            supply, { v -> supply = v.filter { it.isDigit() }.take(7) },
                            label = { Text("Тираж") }, placeholder = { Text("без лимита") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    done?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Button(
                        enabled = canCreate,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = {
                            val uri = image ?: return@Button
                            busy = true; error = null; done = null
                            scope.launch {
                                runCatching {
                                    val fileId = repo.uploadGiftImage(uri)
                                    repo.api.createGiftItem(title.trim(), description.trim(), fileId, price.toLong(), supply.toIntOrNull()?.takeIf { it > 0 }, caption.trim(), animation)
                                }.onSuccess {
                                    done = "«${it.title}» на витрине"
                                    image = null; title = ""; description = ""; price = ""; supply = ""; caption = ""
                                    reload++
                                }.onFailure { error = it.userMessage() }
                                busy = false
                            }
                        },
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Создать NFT")
                    }
                }
            }
        }
        if (items.isNotEmpty()) item {
            Text("Все подарки: эмодзи и NFT", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        items(items, key = { it.id }) { g ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.clickable { editing = g }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GiftImage(g, Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (g.kind == "emoji") "Эмодзи · " else "NFT · ") + g.title,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        FluxAmount(g.price, iconSize = 14.dp)
                        Text(
                            "Продано ${g.sold}" + (g.supply?.let { " из $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = g.active, onCheckedChange = { v ->
                        scope.launch {
                            runCatching { repo.api.updateGiftItem(g.id, active = v) }
                                .onSuccess { u -> items = items.map { if (it.id == u.id) u else it } }
                                .onFailure { error = it.userMessage() }
                        }
                    })
                }
            }
        }
    }

    editing?.let { g ->
        var newPrice by remember(g.id) { mutableStateOf(g.price.toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(g.title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GiftImage(g, Modifier.size(120.dp))
                    TextButton(onClick = { editing = null; deleting = g }) {
                        Text("Удалить подарок навсегда", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        newPrice, { v -> newPrice = v.filter { it.isDigit() }.take(9) },
                        label = { Text("Цена, FLUX") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        if (g.active) "Подарок на витрине. Выключите переключатель, чтобы убрать его из продажи."
                        else "Подарок снят с продажи. Купленные экземпляры остаются у владельцев.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = (newPrice.toLongOrNull() ?: 0) > 0, onClick = {
                    val p = newPrice.toLong()
                    editing = null
                    scope.launch {
                        runCatching { repo.api.updateGiftItem(g.id, price = p) }
                            .onSuccess { u -> items = items.map { if (it.id == u.id) u else it } }
                            .onFailure { error = it.userMessage() }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } },
        )
    }

    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Удалить «${g.title}»?") },
            text = {
                Text(
                    "Подарок пропадёт из магазина и из профилей всех, у кого он есть" +
                        (if (g.sold > 0) " (продано ${g.sold})" else "") + ". FLUX за него не возвращаются. Это нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { repo.api.deleteGiftItem(g.id) }
                            .onSuccess { items = items.filterNot { it.id == g.id } }
                            .onFailure { error = it.userMessage() }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

/** Превью анимации для ещё не загруженной картинки: те же движения, что у готового подарка. */
@Composable
private fun AnimatedPreview(animation: String, caption: String, content: @Composable () -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "preview")
    val wave by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "wave",
    )
    val loop by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "loop",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                when (animation) {
                    "bounce" -> translationY = -wave * size.height * 0.08f
                    "pulse" -> { scaleX = 0.9f + 0.12f * wave; scaleY = 0.9f + 0.12f * wave }
                    "sway" -> rotationZ = -10f + 20f * wave
                    "shake" -> rotationZ = kotlin.math.sin(loop * 2 * Math.PI * 6).toFloat() * 6f * (if (loop < 0.35f) 1f else 0f)
                    "spin" -> rotationY = loop * 360f
                    "float" -> { translationY = -wave * size.height * 0.1f; rotationZ = -4f + 8f * wave }
                    "shine" -> { scaleX = 0.97f + 0.05f * wave; scaleY = 0.97f + 0.05f * wave }
                }
            },
            contentAlignment = Alignment.Center,
        ) { content() }
        if (caption.isNotBlank()) Text(
            caption,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(app.ryzik.chat.ui.flux.FluxGradient))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
