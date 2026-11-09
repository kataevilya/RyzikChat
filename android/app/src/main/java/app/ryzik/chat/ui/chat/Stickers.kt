package app.ryzik.chat.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.StickerPack
import app.ryzik.chat.data.stickerPackLink
import app.ryzik.chat.data.userMessage
import app.ryzik.chat.ui.components.EmptyIcon
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Какой набор стикеров сейчас открыт на просмотр (по нажатию на стикер или ссылку). */
object StickerViewer {
    val packId = MutableStateFlow<String?>(null)
    fun open(id: String) { packId.value = id }
    fun close() { packId.value = null }
}

/**
 * Панель стикеров из меню скрепки: свои наборы, отправка по нажатию, создание стикера
 * из картинки, новый набор и «поделиться набором».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StickerSheet(chatId: String, replyTo: String?, onSent: () -> Unit, onDismiss: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by repo.stickerPacks.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var newPack by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<StickerPack?>(null) }
    LaunchedEffect(Unit) { runCatching { repo.refreshStickers() } }
    val pack = packs.firstOrNull { it.id == selected } ?: packs.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val target = pack
        if (uri != null && target != null) {
            busy = true
            scope.launch {
                runCatching { repo.createSticker(target.id, uri) }.onFailure { error = it.userMessage() }
                busy = false
            }
        }
    }
    fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Стикеры", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(packs, key = { it.id }) { p ->
                    val sel = p.id == pack?.id
                    Text(
                        p.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { selected = p.id }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                item(key = "new") {
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { newPack = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Новый набор", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }

            if (pack == null) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyIcon(Icons.Rounded.EmojiEmotions)
                    Spacer(Modifier.height(12.dp))
                    Text("Сделайте свой первый набор: выберите картинку, и она станет стикером.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { newPack = true }) { Text("Создать набор") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (pack.isMine) "Ваш набор · ${pack.stickers.size}" else "Набор друга · ${pack.stickers.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = { repo.sharePack(chatId, pack); onSent() }) { Icon(Icons.Rounded.Share, "Поделиться набором в этом чате") }
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Набор стикеров «${pack.title}» в RyzikChat: ${stickerPackLink(pack.id)}"),
                                "Поделиться набором",
                            ))
                        }
                    }) { Icon(Icons.Rounded.Share, "Поделиться набором в другом приложении", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { confirmDelete = pack }) { Icon(Icons.Rounded.Delete, if (pack.isMine) "Удалить набор" else "Убрать набор") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    if (pack.isMine) item(key = "create") {
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(enabled = !busy) { pickImage() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Создать", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    items(pack.stickers, key = { it.id }) { st ->
                        AsyncImage(
                            model = repo.avatarUrl(st.fileId),
                            contentDescription = "Стикер",
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { repo.sendSticker(chatId, pack, st, replyTo); onSent() },
                                    onLongClick = {
                                        if (pack.isMine) scope.launch { runCatching { repo.removeSticker(pack.id, st.id) }.onFailure { error = it.userMessage() } }
                                    },
                                )
                                .padding(4.dp),
                        )
                    }
                }
                if (pack.isMine) Text(
                    "Нажмите на стикер, чтобы отправить. Долгое нажатие убирает его из набора.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (newPack) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPack = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Новый набор") },
            text = { OutlinedTextField(title, { title = it.take(64) }, label = { Text("Название") }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    newPack = false
                    scope.launch {
                        runCatching { repo.createStickerPack(title) }
                            .onSuccess { selected = it.id; pickImage() }
                            .onFailure { error = it.userMessage() }
                    }
                }) { Text("Создать и выбрать картинку") }
            },
            dismissButton = { TextButton(onClick = { newPack = false }) { Text("Отмена") } },
        )
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (p.isMine) "Удалить набор «${p.title}»?" else "Убрать набор «${p.title}»?") },
            text = { Text(if (p.isMine) "Набор пропадёт у всех, кто его добавил. Уже отправленные стикеры останутся в чатах." else "Набор пропадёт из вашего списка. Добавить его снова можно по ссылке.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    selected = null
                    scope.launch {
                        runCatching { if (p.isMine) repo.deleteStickerPack(p.id) else repo.setPackAdded(p.id, false) }
                            .onFailure { error = it.userMessage() }
                    }
                }) { Text(if (p.isMine) "Удалить" else "Убрать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

/** Просмотр набора по нажатию на стикер или ссылку: можно добавить его к себе. */
@Composable
fun StickerPackDialog(packId: String, onDismiss: () -> Unit) {
    val repo = RyzikApp.instance.repo
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf<StickerPack?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(packId) {
        runCatching { repo.stickerPack(packId) }.onSuccess { pack = it }.onFailure { error = it.userMessage() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(pack?.title ?: "Набор стикеров") },
        text = {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                pack == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(pack!!.stickers, key = { it.id }) { st ->
                        AsyncImage(repo.avatarUrl(st.fileId), "Стикер", Modifier.aspectRatio(1f).padding(2.dp))
                    }
                }
            }
        },
        confirmButton = {
            val p = pack
            if (p != null && !p.isMine) {
                FilledTonalButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.setPackAdded(p.id, !p.isAdded) }.onSuccess { pack = it }.onFailure { error = it.userMessage() }
                        busy = false
                    }
                }) { Text(if (p.isAdded) "Убрать набор" else "Добавить набор") }
            } else TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = { if (pack?.isMine == false) TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
