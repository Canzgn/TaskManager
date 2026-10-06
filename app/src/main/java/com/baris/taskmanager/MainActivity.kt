package com.baris.taskmanager

import android.os.Bundle
import android.app.DatePickerDialog
import android.content.res.Configuration
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baris.taskmanager.ui.theme.TaskManagerTheme
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

private enum class Screen { LOGIN, REGISTER, LIST, DETAIL, EDIT }
private enum class StatusFilter(val label: String) { ALL("Tümü"), OPEN("Yapılacak"), DONE("Tamamlanan") }
private enum class SortOrder(val label: String) { NEWEST("En yeni"), OLDEST("En eski"), TITLE("Başlık A-Z") }
private val categories = listOf("Kişisel", "İş", "Okul", "Diğer")

private fun calendarFor(day: String): Calendar = Calendar.getInstance().apply {
    val parts = day.split("-").map { it.toInt() }
    set(parts[0], parts[1] - 1, parts[2])
}

private fun selectedDayLabel(day: String): String =
    SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR")).format(calendarFor(day).time)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
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
    var selectedDay by remember { mutableStateOf(dayKey()) }
    var tasks by remember { mutableStateOf(if (userId > 0) database.tasks(userId, selectedDay) else emptyList()) }
    var dataVersion by remember { mutableIntStateOf(0) }

    fun refresh() { tasks = database.tasks(userId, selectedDay); dataVersion++ }
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
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it; refresh() },
                calendarVersion = dataVersion,
                markedDays = { month -> database.markedDays(userId, month) },
                plannedDays = { month -> database.plannedDays(userId, month) },
                onLogout = {
                    session.edit().remove("user_id").apply()
                    userId = -1L
                    tasks = emptyList()
                    screen = Screen.LOGIN
                },
                onAdd = { selectedTask = null; screen = Screen.EDIT },
                onOpen = { selectedTask = it; screen = Screen.DETAIL },
                onToggle = { task -> database.setDone(userId, task.id, selectedDay, !task.done); refresh() }
            )
            Screen.DETAIL -> selectedTask?.let { task -> TaskDetailScreen(
                task = task, selectedDay = selectedDay,
                onBack = { screen = Screen.LIST },
                onEdit = { screen = Screen.EDIT },
                onDelete = { database.deleteTask(userId, task.id); refresh(); screen = Screen.LIST },
                onToggle = {
                    database.setDone(userId, task.id, selectedDay, !task.done)
                    refresh()
                    selectedTask = tasks.firstOrNull { it.id == task.id }
                }
            ) }
            Screen.EDIT -> TaskEditScreen(
                task = selectedTask, selectedDay = selectedDay,
                onBack = { screen = if (selectedTask == null) Screen.LIST else Screen.DETAIL },
                onSave = { title, description, category, repeatDaily, plannedDate ->
                    if (database.saveTask(userId, selectedTask, title, description, category, repeatDaily, plannedDate)) {
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center) {
            Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(30.dp))
        Text("GÜNLÜK PLANIM", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Yeni bir başlangıç" else "Tekrar hoş geldin", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Hesabını oluştur, alışkanlıklarına yer aç." else "Günlük ritmine kaldığın yerden devam et.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        if (register) {
            OutlinedTextField(name, { name = it; error = "" }, label = { Text("Adın") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(email, { email = it; error = "" }, label = { Text("E-posta") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it; error = "" }, label = { Text("Şifre") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmation, { confirmation = it; error = "" }, label = { Text("Şifre tekrar") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
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
        }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text(if (register) "Hesap oluştur" else "Giriş yap", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSwitch, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(name: String, tasks: List<Task>, selectedDay: String,
                           onSelectDay: (String) -> Unit, calendarVersion: Int,
                           markedDays: (String) -> Set<String>,
                           plannedDays: (String) -> Set<String>, onLogout: () -> Unit, onAdd: () -> Unit,
                           onOpen: (Task) -> Unit, onToggle: (Task) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StatusFilter.ALL) }
    var sort by remember { mutableStateOf(SortOrder.NEWEST) }
    var category by remember { mutableStateOf("Tüm kategoriler") }
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val visible = tasks.filter { task ->
        (task.title.contains(query, ignoreCase = true) || task.description.contains(query, ignoreCase = true)) &&
            (category == "Tüm kategoriler" || task.category == category) &&
            when (filter) { StatusFilter.ALL -> true; StatusFilter.OPEN -> !task.done; StatusFilter.DONE -> task.done }
    }.let { list -> when (sort) {
        SortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
        SortOrder.OLDEST -> list.sortedBy { it.createdAt }
        SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
    } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(
            title = { Text("Günlük Planım", fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = onLogout) { Text("Çıkış") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        ) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, text = { Text("Yeni aktivite", fontWeight = FontWeight.SemiBold) },
                icon = { Text("+", fontSize = 24.sp) }, shape = RoundedCornerShape(16.dp))
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Merhaba, $name 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Küçük adımlar, büyük alışkanlıklar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { CalendarCard(selectedDay, onSelectDay, calendarVersion, markedDays, plannedDays) }
            item { ProgressCard(tasks) }
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Aktivitelerde ara") }, singleLine = true,
                    leadingIcon = { Text("⌕", fontSize = 26.sp) },
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Temizle") } },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusFilter.entries.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option },
                            label = { Text(option.label) }, shape = RoundedCornerShape(12.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("$category ▾", maxLines = 1)
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            (listOf("Tüm kategoriler") + categories).forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryMenu = false })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text("${sort.label} ▾") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortMenu = false })
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (selectedDay == dayKey()) "Bugünün planı" else "Günün planı",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${visible.size} aktivite", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (visible.isEmpty()) item {
                EmptyTasks(hasTasks = tasks.isNotEmpty(), onAdd = onAdd)
            } else items(visible, key = { it.id }) { task ->
                TaskListCard(task, onOpen = { onOpen(task) }, onToggle = { onToggle(task) })
            }
        }
    }
}

@Composable
private fun CalendarCard(selectedDay: String, onSelectDay: (String) -> Unit,
                         calendarVersion: Int, markedDays: (String) -> Set<String>,
                         plannedDays: (String) -> Set<String>) {
    var shownMonth by remember { mutableStateOf(selectedDay.take(7)) }
    val year = shownMonth.take(4).toInt()
    val month = shownMonth.takeLast(2).toInt()
    val first = Calendar.getInstance().apply { set(year, month - 1, 1) }
    val leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val marked = remember(shownMonth, calendarVersion) { markedDays(shownMonth) }
    val planned = remember(shownMonth, calendarVersion) { plannedDays(shownMonth) }
    fun moveMonth(amount: Int) {
        first.add(Calendar.MONTH, amount)
        shownMonth = String.format(Locale.ROOT, "%04d-%02d", first.get(Calendar.YEAR), first.get(Calendar.MONTH) + 1)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(SimpleDateFormat("MMMM yyyy", Locale("tr", "TR")).format(first.time)
                    .replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { moveMonth(-1) }) { Text("‹", fontSize = 24.sp) }
                    TextButton(onClick = { shownMonth = dayKey().take(7); onSelectDay(dayKey()) }) { Text("Bugün") }
                    TextButton(onClick = { moveMonth(1) }) { Text("›", fontSize = 24.sp) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz").forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            repeat((leading + daysInMonth + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { weekday ->
                        val number = week * 7 + weekday - leading + 1
                        val key = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, number)
                        val valid = number in 1..daysInMonth
                        val selected = valid && key == selectedDay
                        Column(Modifier.weight(1f).height(48.dp)
                            .then(if (valid) Modifier.clickable { onSelectDay(key) } else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(34.dp).background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (valid) Text(number.toString(), color = if (selected)
                                    MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected || key == dayKey()) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (valid && (key in marked || key in planned))
                                Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                }
            }
            Text(selectedDayLabel(selectedDay), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProgressCard(tasks: List<Task>) {
    val completed = tasks.count { it.done }
    val ratio = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("GÜNLÜK İLERLEME", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("$completed / ${tasks.size} tamamlandı", color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("${(ratio * 100).toInt()}%", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f), CircleShape)) {
                Box(Modifier.fillMaxWidth(ratio).height(8.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape))
            }
            Spacer(Modifier.height(10.dp))
            Text(when {
                tasks.isEmpty() -> "Bugüne küçük bir hedef ekleyerek başla."
                completed == tasks.size -> "Harika! Bugünün bütün adımlarını tamamladın."
                completed == 0 -> "İlk adımı atınca devamı daha kolay gelir."
                else -> "Güzel gidiyorsun, ritmini koru!"
            }, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TaskListCard(task: Task, onOpen: () -> Unit, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(task.description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text("${task.category.uppercase()} · ${if (task.repeatDaily) "HER GÜN" else "TEK SEFERLİK"}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 28.sp)
        }
    }
}

@Composable
private fun EmptyTasks(hasTasks: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(if (hasTasks) "⌕" else "✓", color = MaterialTheme.colorScheme.primary, fontSize = 36.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(if (hasTasks) "Sonuç bulunamadı" else "Bugün için plan yok", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text(if (hasTasks) "Aramayı veya filtreleri değiştirebilirsin." else "Yeni bir alışkanlık veya plan ekle.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasTasks) TextButton(onClick = onAdd) { Text("Aktivite oluştur") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailScreen(task: Task, selectedDay: String, onBack: () -> Unit, onEdit: () -> Unit,
                             onDelete: () -> Unit, onToggle: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Aktivite detayı", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("‹ Geri") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(task.category.uppercase(), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(task.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (task.repeatDaily) "Her gün · ${selectedDayLabel(task.plannedDate)} tarihinden beri"
                        else "Tek seferlik · ${selectedDayLabel(task.plannedDate)}",
                        color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()
                    Text("AÇIKLAMA", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(task.description.ifBlank { "Bu aktivite için açıklama eklenmedi." })
                    HorizontalDivider()
                    Text("Oluşturulma: ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr", "TR")).format(Date(task.createdAt))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.done, onCheckedChange = { onToggle() })
                    Text(if (task.done) "${selectedDayLabel(selectedDay)} tamamlandı" else "Bu gün için tamamlandı olarak işaretle",
                        fontWeight = FontWeight.Medium)
                }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Aktiviteyi düzenle")
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)) { Text("Aktiviteyi sil", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Aktivite silinsin mi?") },
        text = { Text("${task.title} kalıcı olarak silinecek.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditScreen(task: Task?, selectedDay: String, onBack: () -> Unit,
                           onSave: (String, String, String, Boolean, String) -> Boolean) {
    val context = LocalContext.current
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var category by remember(task?.id) { mutableStateOf(task?.category ?: categories.first()) }
    var repeatDaily by remember(task?.id) { mutableStateOf(task?.repeatDaily ?: true) }
    var plannedDate by remember(task?.id) { mutableStateOf(task?.plannedDate ?: selectedDay) }
    var error by remember(task?.id) { mutableStateOf("") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(if (task == null) "Yeni aktivite" else "Aktiviteyi düzenle", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("‹ Geri") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (task == null) "Alışkanlığını veya tek seferlik planını ekle." else "Aktivite bilgilerini güncelle.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it; error = "" }, label = { Text("Aktivite başlığı *") },
                supportingText = { Text("En fazla 80 karakter") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Açıklama") }, minLines = 3,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Text("Kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { option ->
                    FilterChip(category == option, { category = option }, label = { Text(option) },
                        shape = RoundedCornerShape(12.dp))
                }
            }
            Text("Tekrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(repeatDaily, { repeatDaily = true }, label = { Text("Her gün") })
                FilterChip(!repeatDaily, { repeatDaily = false }, label = { Text("Tek seferlik") })
            }
            Text(if (repeatDaily) "Seçilen tarihten itibaren her gün görünür." else "Yalnızca seçilen günde görünür.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val date = calendarFor(plannedDate)
                DatePickerDialog(context, { _, year, month, day ->
                    plannedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("${if (repeatDaily) "Başlangıç" else "Planlanan gün"}: ${selectedDayLabel(plannedDate)}")
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                error = when {
                    title.isBlank() -> "Başlık boş olamaz."
                    title.trim().length > 80 -> "Başlık en fazla 80 karakter olabilir."
                    !onSave(title, description, category, repeatDaily, plannedDate) -> "Aktivite kaydedilemedi."
                    else -> ""
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Aktiviteyi kaydet") }
        }
    }
}
