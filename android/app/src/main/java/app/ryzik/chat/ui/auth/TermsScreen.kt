package app.ryzik.chat.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

val TermsSections = listOf(
    "1. Общие положения" to "RyzikChat — мессенджер для личного и группового общения. Создавая аккаунт или входя в него, вы принимаете эти правила. Если вы с ними не согласны, не пользуйтесь сервисом.",
    "2. Аккаунт" to "Вы отвечаете за сохранность своего пароля. Пароль защищает и ключи шифрования: восстановить переписку без него невозможно. Один человек — один основной аккаунт; выдавать себя за другого человека запрещено.",
    "3. Запрещено" to "Рассылать спам и вредоносные файлы; распространять незаконный контент; угрожать, травить и оскорблять других; публиковать чужие личные данные без согласия; пытаться взломать сервис или других пользователей.",
    "4. Шифрование и данные" to "Сообщения и файлы шифруются на вашем устройстве. Сервер хранит только зашифрованные данные и служебную информацию: имя пользователя, отображаемое имя, аватар, время отправки сообщений и участников чатов.",
    "5. Бейджи" to "Бейджи рядом с именем выдаёт только администрация RyzikChat. Их нельзя купить или передать другому пользователю. Администрация может снять бейдж, если он выдан по ошибке или правила нарушены.",
    "6. Модерация" to "Если на вас пожаловались и нарушение подтвердилось, администрация может ограничить или удалить аккаунт. Содержимое зашифрованных чатов администрация прочитать не может, поэтому жалобы рассматриваются по присланным материалам.",
    "7. Изменения" to "Правила могут обновляться. О важных изменениях мы сообщим в приложении. Продолжая пользоваться RyzikChat после изменений, вы соглашаетесь с новой редакцией.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Правила использования") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            itemsIndexed(TermsSections) { _, (title, text) ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
