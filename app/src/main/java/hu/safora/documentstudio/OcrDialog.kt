package hu.safora.documentstudio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager

@Composable fun OcrDialog(doc: Doc, english: Boolean, onDismiss: () -> Unit, onTextChanged: () -> Unit) {
    val context = LocalContext.current
    val tr: (String, String) -> String = { hu, en -> if (english) en else hu }
    var info by remember { mutableStateOf<WorkInfo?>(null) }
    var text by remember { mutableStateOf(DocumentOcr.text(context, doc.id)) }
    var indexed by remember { mutableStateOf(DocumentOcr.index(context, doc.id).exists()) }
    DisposableEffect(doc.id) {
        val live = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("ocr:${doc.id}")
        val observer = androidx.lifecycle.Observer<List<WorkInfo>> { work ->
            info = work.firstOrNull { !it.state.isFinished } ?: work.lastOrNull()
            if (work.any { it.state == WorkInfo.State.SUCCEEDED }) { text = DocumentOcr.text(context, doc.id); indexed = DocumentOcr.index(context, doc.id).exists(); onTextChanged() }
        }
        live.observeForever(observer)
        onDispose { live.removeObserver(observer) }
    }
    LaunchedEffect(doc.id) { DocumentOcr.enqueue(context, doc) }
    val active = info?.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Felismert szöveg", "Recognized text")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Offline HU/EN felismerés. Ellenőrizd a fontos adatokat.", "Offline HU/EN recognition. Check important details."), style = MaterialTheme.typography.bodySmall)
            if (active) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(tr("Felismerés folyamatban", "Recognition in progress") + " ${info?.progress?.getInt("page", 0) ?: 0}/${info?.progress?.getInt("total", 0) ?: 0}") }
            if (info?.state == WorkInfo.State.FAILED) Text(tr("A felismerés sikertelen. Próbáld újra egy élesebb képpel.", "Recognition failed. Retry or use a clearer image."), color = MaterialTheme.colorScheme.error)
            if (indexed && text.isBlank() && !active) Text(tr("Nem található olvasható latin betűs szöveg.", "No readable Latin text found."))
            SelectionContainer(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { Text(text) }
            Row {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(doc.name, text))
                }, enabled = text.isNotBlank()) { Text(tr("Másolás", "Copy")) }
                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Szöveg megosztása", "Share text"))) }, enabled = text.isNotBlank()) { Text(tr("Megosztás", "Share")) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Bezárás", "Close")) } }, dismissButton = { TextButton(onClick = { DocumentOcr.enqueue(context, doc, true) }, enabled = !active) { Text(tr("Újrafelismerés", "Retry OCR")) } })
}
