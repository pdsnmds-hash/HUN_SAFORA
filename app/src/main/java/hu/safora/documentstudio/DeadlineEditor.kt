package hu.safora.documentstudio

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Composable fun NotificationStatus(english: Boolean) {
    val context = LocalContext.current
    // Re-evaluate when returning from app settings.
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val owner = context as androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val enabled = remember(revision) { ReminderScheduler.notificationsEnabled(context) }
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(if (english) if (enabled) "Notifications enabled" else "Notifications are blocked" else if (enabled) "Értesítések engedélyezve" else "Az értesítések le vannak tiltva")
            Text(if (english) "Battery saving may delay delivery. Reminders survive app restarts and device reboots." else "Az energiatakarékosság késleltetheti az értesítést. Az emlékeztetők újraindítás után is megmaradnak.", style = MaterialTheme.typography.bodySmall)
            if (!enabled) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(if (english) "Enable in settings" else "Engedélyezés a beállításokban") }
        }
    }
}

@Composable fun DeadlineEditor(store: Store, initial: Deadline? = null, documentId: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tr: (String, String) -> String = { hu, en -> if (store.english) en else hu }
    val defaultTime = remember { LocalDateTime.now().plusHours(1).withSecond(0).withNano(0) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var date by rememberSaveable { mutableStateOf(initial?.date ?: defaultTime.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial?.time ?: defaultTime.toLocalTime().toString()) }
    var linked by rememberSaveable { mutableStateOf(initial?.documentId ?: documentId) }
    var lead by rememberSaveable { mutableLongStateOf(initial?.leadMinutes ?: 1440) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeat ?: "NONE") }
    var chooseDoc by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val requester = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val validDate = runCatching { LocalDate.parse(date) }.getOrDefault(defaultTime.toLocalDate())
    val validTime = runCatching { LocalTime.parse(time) }.getOrDefault(defaultTime.toLocalTime())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr(if (initial == null) "Új emlékeztető" else "Emlékeztető szerkesztése", if (initial == null) "New reminder" else "Edit reminder")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(tr("Megnevezés", "Title")) }, singleLine = true)
            Row {
                OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, validDate.year, validDate.monthValue - 1, validDate.dayOfMonth).show() }) { Text(date) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString() }, validTime.hour, validTime.minute, true).show() }) { Text(time) }
            }
            OutlinedButton(onClick = { chooseDoc = true }, modifier = Modifier.fillMaxWidth()) { Text(store.docs.find { it.id == linked && store.canAccess(it) }?.name ?: tr("Dokumentum csatolása (opcionális)", "Link document (optional)"), maxLines = 2) }
            Text(tr("Előzetes értesítés", "Advance notice"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0L to tr("Nincs", "None"), 60L to tr("1 óra", "1 hour"), 1440L to tr("1 nap", "1 day"), 10080L to tr("1 hét", "1 week")).forEach { (value, label) -> FilterChip(selected = lead == value, onClick = { lead = value }, label = { Text(label) }) }
            }
            Text(tr("Ismétlődés", "Repeat"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NONE" to tr("Egyszer", "Once"), "DAILY" to tr("Naponta", "Daily"), "WEEKLY" to tr("Hetente", "Weekly"), "MONTHLY" to tr("Havonta", "Monthly")).forEach { (value, label) -> FilterChip(selected = repeat == value, onClick = { repeat = value }, label = { Text(label) }) }
            }
            Text(tr("Helyi idő. Az Android energiatakarékos módja késleltetheti az értesítést. Az előzetes értesítés csak jövőbeli időpontra készül.", "Local time. Android battery saving may delay notifications. Advance notices are scheduled only when still in the future."), style = MaterialTheme.typography.bodySmall)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val d = Deadline(initial?.id ?: UUID.randomUUID().toString(), title.trim(), date, initial?.done ?: false, time, linked, lead, repeat, validDate.dayOfMonth)
        if (!DeadlineRules.valid(d)) { error = tr("Ellenőrizd a dátumot és az időt.", "Check the date and time."); return@TextButton }
        if (initial == null && DeadlineRules.dueAt(d) <= System.currentTimeMillis()) { error = tr("Válassz jövőbeli időpontot.", "Choose a future date and time."); return@TextButton }
        if (store.saveDeadline(d)) {
            if (Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.notificationsEnabled(context)) requester.launch(Manifest.permission.POST_NOTIFICATIONS)
            onDismiss()
        } else error = tr("Az emlékeztető nem menthető.", "Could not save reminder.")
    }, enabled = title.isNotBlank()) { Text(tr("Mentés", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Mégse", "Cancel")) } })
    if (chooseDoc) AlertDialog(onDismissRequest = { chooseDoc = false }, title = { Text(tr("Dokumentum", "Document")) }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { linked = ""; chooseDoc = false }) { Text(tr("Nincs csatolmány", "No linked document")) }
            store.docs.filter{store.canAccess(it)}.forEach { doc -> TextButton(onClick = { linked = doc.id; chooseDoc = false }) { Text(doc.name) } }
        }
    }, confirmButton = { TextButton(onClick = { chooseDoc = false }) { Text(tr("Bezárás", "Close")) } })
}

@Composable fun ReminderCard(d: Deadline, store: Store) {
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val tr: (String, String) -> String = { hu, en -> if (store.english) en else hu }
    val overdue = !d.done && runCatching { DeadlineRules.dueAt(d) < System.currentTimeMillis() }.getOrDefault(false)
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(d.done, { store.toggleDeadline(d.id) })
                Column(Modifier.weight(1f).clickable { edit = true }) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall)
                    Text("${d.date} ${d.time}" + if (overdue) tr(" · Lejárt", " · Overdue") else "", style = MaterialTheme.typography.bodySmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.repeat != "NONE") Text(when (d.repeat) { "DAILY" -> tr("Naponta", "Daily"); "WEEKLY" -> tr("Hetente", "Weekly"); else -> tr("Havonta", "Monthly") }, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { remove = true }) { Icon(Icons.Outlined.DeleteOutline, tr("Törlés", "Delete")) }
            }
            store.docs.find { it.id == d.documentId && store.canAccess(it) }?.let { doc -> TextButton(onClick = { store.open(doc) }) { Text(doc.name, maxLines = 1) } }
        }
    }
    if (edit) DeadlineEditor(store, initial = d, onDismiss = { edit = false })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text(tr("Emlékeztető törlése?", "Delete reminder?")) }, text = { Text(d.title) }, confirmButton = { TextButton(onClick = { store.deleteDeadline(d.id); remove = false }) { Text(tr("Törlés", "Delete")) } }, dismissButton = { TextButton(onClick = { remove = false }) { Text(tr("Mégse", "Cancel")) } })
}
