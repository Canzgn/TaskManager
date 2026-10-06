package com.baris.taskmanager

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baris.taskmanager.ui.theme.TaskManagerTheme
import java.text.DateFormat
import java.util.Date

private enum class Screen { LOGIN, REGISTER, LIST, DETAIL, EDIT }
private enum class StatusFilter(val label: String) { ALL("Tümü"), OPEN("Yapılacak"), DONE("Tamamlanan") }
private enum class SortOrder(val label: String) { NEWEST("En yeni"), OLDEST("En eski"), TITLE("Başlık A-Z") }
private val categories = listOf("Kişisel", "İş", "Okul", "Diğer")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase(this)
        val session = getSharedPreferences("session", MODE_PRIVATE)
        setContent { TaskManagerTheme { TaskManagerApp(database, session) } }
    }
}

@Composable
private fun TaskManagerApp(database: AppDatabase, session: android.content.SharedPreferences) {
    var userId by remember { mutableLongStateOf(session.getLong("user_id", -1L).takeIf(database::userExists) ?: -1L) }
    var screen by remember { mutableStateOf(if (userId > 0) Screen.LIST else Screen.LOGIN) }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var tasks by remember { mutableStateOf(if (userId > 0) database.tasks(userId) else emptyList()) }

    fun refresh() { tasks = database.tasks(userId) }
    fun signedIn(id: Long) {
        userId = id
        session.edit().putLong("user_id", id).apply()
        refresh()
        screen = Screen.LIST
    }

    Surface(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.LOGIN -> AuthScreen(
                register = false,
                onSwitch = { screen = Screen.REGISTER },
                onSubmit = { _, email, password ->
                    database.login(email, password)?.also(::signedIn) != null
                }
            )
            Screen.REGISTER -> AuthScreen(
                register = true,
                onSwitch = { screen = Screen.LOGIN },
                onSubmit = { name, email, password ->
                    database.register(name, email, password)?.also(::signedIn) != null
                }
            )
            Screen.LIST -> TaskListScreen(
                name = database.userName(userId), tasks = tasks,
                onLogout = {
                    session.edit().remove("user_id").apply()
                    userId = -1L
                    tasks = emptyList()
                    screen = Screen.LOGIN
                },
                onAdd = { selectedTask = null; screen = Screen.EDIT },
                onOpen = { selectedTask = it; screen = Screen.DETAIL },
                onToggle = { task -> database.setDone(userId, task.id, !task.done); refresh() }
            )
            Screen.DETAIL -> selectedTask?.let { task -> TaskDetailScreen(
                task = task,
                onBack = { screen = Screen.LIST },
                onEdit = { screen = Screen.EDIT },
                onDelete = { database.deleteTask(userId, task.id); refresh(); screen = Screen.LIST },
                onToggle = {
                    database.setDone(userId, task.id, !task.done)
                    refresh()
                    selectedTask = tasks.firstOrNull { it.id == task.id }
                }
            ) }
            Screen.EDIT -> TaskEditScreen(
                task = selectedTask,
                onBack = { screen = if (selectedTask == null) Screen.LIST else Screen.DETAIL },
                onSave = { title, description, category ->
                    if (database.saveTask(userId, selectedTask, title, description, category)) {
                        refresh()
                        selectedTask = null
                        screen = Screen.LIST
                        true
                    } else false
                }
            )
        }
    }
}

@Composable
private fun AuthScreen(register: Boolean, onSwitch: () -> Unit, onSubmit: (String, String, String) -> Boolean) {
    var name by remember(register) { mutableStateOf("") }
    var email by remember(register) { mutableStateOf("") }
    var password by remember(register) { mutableStateOf("") }
    var confirmation by remember(register) { mutableStateOf("") }
    var error by remember(register) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(if (register) "Hesap oluştur" else "Tekrar hoş geldin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Görevlerini düzenlemeye başla" else "Görevlerine devam et")
        Spacer(Modifier.height(28.dp))
        if (register) {
            OutlinedTextField(name, { name = it; error = "" }, label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(email, { email = it; error = "" }, label = { Text("E-posta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it; error = "" }, label = { Text("Şifre") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmation, { confirmation = it; error = "" }, label = { Text("Şifre tekrar") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            error = when {
                register && name.trim().length < 2 -> "Ad en az 2 karakter olmalı."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Geçerli bir e-posta gir."
                password.length < 6 -> "Şifre en az 6 karakter olmalı."
                register && password != confirmation -> "Şifreler eşleşmiyor."
                !onSubmit(name, email, password) -> if (register) "Bu e-posta zaten kayıtlı." else "E-posta veya şifre hatalı."
                else -> ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Kayıt ol" else "Giriş yap") }
        TextButton(onClick = onSwitch, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(name: String, tasks: List<Task>, onLogout: () -> Unit, onAdd: () -> Unit,
                           onOpen: (Task) -> Unit, onToggle: (Task) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StatusFilter.ALL) }
    var sort by remember { mutableStateOf(SortOrder.NEWEST) }
    val visible = tasks.filter { task ->
        (task.title.contains(query, ignoreCase = true) || task.description.contains(query, ignoreCase = true)) &&
            when (filter) { StatusFilter.ALL -> true; StatusFilter.OPEN -> !task.done; StatusFilter.DONE -> task.done }
    }.let { list -> when (sort) {
        SortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
        SortOrder.OLDEST -> list.sortedBy { it.createdAt }
        SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
    } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Merhaba, $name") }, actions = { TextButton(onClick = onLogout) { Text("Çıkış") } }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+", style = MaterialTheme.typography.headlineMedium) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Görevlerim", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${tasks.count { it.done }}/${tasks.size} görev tamamlandı")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it }, label = { Text("Görevlerde ara") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sırala: ")
                SortOrder.entries.forEach { option ->
                    TextButton(onClick = { sort = option }) {
                        Text(option.label, fontWeight = if (sort == option) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (tasks.isEmpty()) "Henüz görev yok. + ile ekle." else "Eşleşen görev bulunamadı.")
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(visible, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(task) }, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.done, onCheckedChange = { onToggle(task) })
                            Column(Modifier.weight(1f)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold)
                                Text("${task.category} • ${if (task.done) "Tamamlandı" else "Yapılacak"}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("›", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailScreen(task: Task, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggle: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Görev detayı") }, navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(task.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(task.description.ifBlank { "Açıklama eklenmedi." })
            Text("Kategori: ${task.category}")
            Text("Oluşturulma: ${DateFormat.getDateTimeInstance().format(Date(task.createdAt))}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(task.done, onCheckedChange = { onToggle() })
                Text(if (task.done) "Tamamlandı" else "Yapılacak")
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Düzenle") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Sil") }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Görev silinsin mi?") },
        text = { Text("${task.title} kalıcı olarak silinecek.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditScreen(task: Task?, onBack: () -> Unit, onSave: (String, String, String) -> Boolean) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var category by remember(task?.id) { mutableStateOf(task?.category ?: categories.first()) }
    var error by remember(task?.id) { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (task == null) "Yeni görev" else "Görevi düzenle") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it; error = "" }, label = { Text("Başlık *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Açıklama") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text("Kategori")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { option -> FilterChip(category == option, { category = option }, label = { Text(option) }) }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                error = when {
                    title.isBlank() -> "Başlık boş olamaz."
                    title.trim().length > 80 -> "Başlık en fazla 80 karakter olabilir."
                    !onSave(title, description, category) -> "Görev kaydedilemedi."
                    else -> ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Kaydet") }
        }
    }
}
