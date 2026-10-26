package hu.safora.documentstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class ScannerActivity : ComponentActivity() {
    private lateinit var store:Store
    override fun onResume(){super.onResume();if(::store.isInitialized)store.refresh()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=Store(this)
        val folder=intent.getStringExtra("folder")?:"Unsorted"
        setContent {
            val english = intent.getBooleanExtra("english", false)
            val dark = intent.getBooleanExtra("dark", true)
            MaterialTheme(colorScheme = saforaColorScheme(dark),typography=responsiveTypography()) {
                SaforaSystemBars(dark)
                VaultAccessScreen(store,folder,onExit={finish()}) {
                    SecureVaultWindow(store.hasVaultPassword(folder))
                    ScannerScreen(english, folder, onFinish = { setResult(RESULT_OK); finish() }, onExit = { finish() })
                }
            }
        }
    }
}

@Composable private fun ScannerScreen(english: Boolean, folder: String, onFinish: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val narrow=windowWidthDp()<360
    val session = remember(folder) { ScanFiles(context, Store(context as android.app.Activity).scanSessionKey(folder)) }
    val scope = rememberCoroutineScope()
    val tr: (String, String) -> String = { hu, en -> if (english) en else hu }
    var error by rememberSaveable { mutableStateOf("") }
    var pages by remember { mutableStateOf(runCatching { session.pages() }.getOrElse { error = tr("A korábbi munkamenet nem olvasható.", "Previous scan session could not be read."); emptyList() }) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var camera by rememberSaveable { mutableStateOf(pages.isEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Scan-${java.time.LocalDate.now()}") }
    var crop by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var captureReady by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val requester = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    fun replacePages(next: List<File>) { session.setOwner(folder); session.save(next); pages = next; selected = selected.coerceIn(0, (next.size - 1).coerceAtLeast(0)); revision++ }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true; error = ""
            try {
                val added = withContext(Dispatchers.IO) {
                    val result = mutableListOf<File>()
                    try {
                        uris.take(30 - pages.size).forEach { uri ->
                            val raw = session.newFile()
                            try {
                                requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> raw.outputStream().use { input.copyTo(it) } }
                                val image = ScanImages.decode(raw)
                                try { ScanImages.write(image, raw) } finally { image.recycle() }
                                result += raw
                            } catch (e: Exception) { raw.delete(); throw e }
                        }
                        result
                    } catch(e: Exception) { result.forEach { it.delete() }; throw e }
                }
                replacePages(pages + added); selected = pages.lastIndex; camera = false
            } catch (e: Exception) { error = tr("A kép nem importálható.", "Could not import the image.") }
            finally { busy = false }
        }
    }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pages.getOrNull(selected)?.path, revision, camera) {
        source = null
        if (!camera) source = pages.getOrNull(selected)?.let { file -> withContext(Dispatchers.IO) { runCatching { ScanImages.decode(file, 1800) }.getOrNull() } }
    }
    DisposableEffect(context) {
        val owner=context as androidx.lifecycle.LifecycleOwner
        val observer=androidx.lifecycle.LifecycleEventObserver{_, event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)permission=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED}
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Normalized corners are reset for each page and after rotation/crop.
    var corners by remember(selected, revision) { mutableStateOf(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f))) }
    fun editPage(rotate: Boolean) {
        if(source == null)return
        scope.launch {
            busy = true; error = ""
            try {
                val target = withContext(Dispatchers.IO) {
                    val full = ScanImages.decode(pages[selected])
                    try {
                        val edited = if (rotate) ScanImages.rotate(full) else ScanImages.crop(full, corners)
                        try { session.newFile().also { ScanImages.write(edited, it) } } finally { edited.recycle() }
                    } finally { full.recycle() }
                }
                replacePages(pages.toMutableList().apply { set(selected, target) }); crop = false
            } catch (e: Exception) { error = tr("A szerkesztés sikertelen. Próbáld újra.", "Could not edit page. Please retry.") }
            finally { busy = false }
        }
    }
    fun exit() { if (busy) return; if (pages.isEmpty()) onExit() else discard = true }
    BackHandler { if (crop) crop = false else if (camera && pages.isNotEmpty()) camera = false else exit() }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val short=maxHeight<640.dp
        Column(Modifier.fillMaxSize().then(if(short)Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { exit() }, enabled = !busy) { Text(tr("Vissza", "Back")) }
                Text(tr("Dokumentumszkenner", "Document scanner"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${pages.size}/30")
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.then(if(short)Modifier.height(180.dp) else Modifier.weight(1f)).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (camera) {
                    if (permission) CameraView(capture, onReady = { captureReady = it }, onError = { error = tr("A kamera nem indítható. Importálhatsz képeket is.", "Camera unavailable. You can import images instead.") })
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("A fényképezéshez kameraengedély szükséges.", "Allow camera access to photograph documents."))
                        Button(onClick = { requester.launch(Manifest.permission.CAMERA) }) { Text(tr("Kamera engedélyezése", "Allow camera")) }
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(tr("Alkalmazásbeállítások", "App settings")) }
                    }
                } else source?.let { image ->
                    if (crop) CropEditor(image, corners, onChange = { corners = it })
                    else Image(image.asImageBitmap(), tr("Kiválasztott oldal", "Selected page"), Modifier.fillMaxSize())
                } ?: Text(tr("Adj hozzá egy oldalt.", "Add a page to begin."))
            }
            if (!camera && pages.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { index, file ->
                        Column(Modifier.border(if (index == selected) 2.dp else 1.dp, if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).clickable(enabled = !busy) { selected = index; crop = false }.padding(6.dp)) {
                            Text("${index + 1}")
                            var thumb by remember(file.path) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(file.path) { thumb = withContext(Dispatchers.IO) { runCatching { ScanImages.decode(file, 100) }.getOrNull() } }
                            thumb?.let { Image(it.asImageBitmap(), "${index + 1}", Modifier.size(48.dp, 60.dp)) }
                        }
                    }
                }
                if (crop) {
                    Text(tr("Húzd a négy sarkot a dokumentum széleihez.", "Drag the four corners to the document edges."), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScannerAction(tr("Mégse", "Cancel"), Icons.Outlined.Close, { crop = false }, Modifier.weight(1f), !busy)
                        ScannerAction(tr("Kivágás alkalmazása", "Apply crop"), Icons.Outlined.Check, { editPage(false) }, Modifier.weight(1.5f), !busy && source != null)
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScannerAction(tr("Kivágás", "Crop"), Icons.Outlined.Crop, { crop = true }, Modifier.weight(1f), !busy && source != null)
                        ScannerAction(tr("Forgatás", "Rotate"), Icons.Outlined.RotateRight, { editPage(true) }, Modifier.weight(1f), !busy && source != null)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScannerArrow(tr("Előző helyre", "Move earlier"), Icons.Outlined.ArrowBack, !busy && selected > 0) {
                            val next = pages.toMutableList(); java.util.Collections.swap(next, selected, selected - 1); replacePages(next); selected--
                        }
                        ScannerArrow(tr("Következő helyre", "Move later"), Icons.Outlined.ArrowForward, !busy && selected < pages.lastIndex) {
                            val next = pages.toMutableList(); java.util.Collections.swap(next, selected, selected + 1); replacePages(next); selected++
                        }
                        ScannerAction(tr("Törlés", "Remove"), Icons.Outlined.DeleteOutline, {
                            replacePages(pages.filterIndexed { i, _ -> i != selected }); if (pages.isEmpty()) camera = true
                        }, Modifier.weight(1f), !busy, danger = true)
                    }
                }
            }
            if (camera) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    busy = true; error = ""
                    val target = session.newFile()
                    capture.targetRotation = (context as ComponentActivity).window.decorView.display.rotation
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(target).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try { replacePages(pages + target); selected = pages.lastIndex; camera = false }
                            catch(e: Exception) { error = tr("A kép nem menthető.", "Could not save photo.") }
                            finally { busy = false }
                        }
                        override fun onError(exception: ImageCaptureException) { target.delete(); busy = false; error = tr("Sikertelen felvétel. Próbáld újra.", "Capture failed. Please retry.") }
                    })
                }, enabled = permission && captureReady && !busy && pages.size < 30) { Text(tr("Fényképezés", "Capture")) }
                TextButton(onClick = { camera = false }, enabled = !busy && pages.isNotEmpty()) { Text(tr("Oldalak", "Review")) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScannerAction(tr("Új oldal", "Add page"), Icons.Outlined.Add, { crop = false; camera = true; permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }, Modifier.weight(1f), !busy && pages.size < 30)
                ScannerAction(if(narrow)tr("Képek", "Photos") else tr("Képek importálása", "Import photos"), Icons.Outlined.PhotoLibrary, { gallery.launch(arrayOf("image/*")) }, Modifier.weight(1f), !busy && pages.size < 30)
            }
            OutlinedTextField(name, { name = it }, label = { Text(tr("PDF neve", "PDF name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            Button(onClick = {
                scope.launch {
                    busy = true; error = ""
                    var target: File? = null
                    try {
                        target = File.createTempFile("scan-", ".pdf", context.cacheDir)
                        withContext(Dispatchers.IO) { ScanImages.exportPdf(pages, target!!) }
                        Store(context as android.app.Activity).addScannedPdf(target!!, name.trim(), folder)
                        session.clear(); onFinish()
                    } catch(e: Exception) { error = tr("A PDF nem menthető. Az oldalak megmaradtak.", "Could not save PDF. Your pages have been kept.") }
                    finally { target?.delete(); busy = false }
                }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !busy && pages.isNotEmpty() && name.isNotBlank() && !crop) { Text(tr("PDF mentése és szövegfelismerés", "Save PDF & recognize text")) }
        }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("Szkennelés félbehagyása?", "Leave this scan?")) }, text = { Text(tr("Az oldalak megőrizhetők a következő megnyitásig.", "Keep your pages and resume next time, or discard them.")) }, confirmButton = { TextButton(onClick = { onExit() }) { Text(tr("Megőrzés és kilépés", "Keep & leave")) } }, dismissButton = { TextButton(onClick = { session.clear(); onExit() }) { Text(tr("Elvetés", "Discard")) } })
}

@Composable private fun CameraView(capture: ImageCapture, onReady: (Boolean) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = context as ComponentActivity
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(view) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                onReady(true)
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; onReady(false); provider?.unbindAll() }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

@Composable private fun CropEditor(image: Bitmap, points: List<PointF>, onChange: (List<PointF>) -> Unit) {
    val current by rememberUpdatedState(points)
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = image.width.toFloat() / image.height
        val width = min(maxWidth.value, maxHeight.value * ratio).dp
        Canvas(Modifier.size(width, width / ratio).pointerInput(image) {
            var corner = -1
            detectDragGestures(onDragStart = { offset -> corner = current.indices.minByOrNull { i -> (Offset(current[i].x * size.width, current[i].y * size.height) - offset).getDistance() } ?: -1 }, onDragEnd = { corner = -1 }, onDragCancel = { corner = -1 }) { change, delta ->
                change.consume()
                if (corner >= 0) {
                    val next = current.mapIndexed { i, p -> if (i == corner) PointF((p.x + delta.x / size.width).coerceIn(0f, 1f), (p.y + delta.y / size.height).coerceIn(0f, 1f)) else PointF(p.x, p.y) }
                    if (ScanImages.validCorners(next)) onChange(next)
                }
            }
        }) {
            drawImage(image.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            val polygon = Path().apply { moveTo(points[0].x * size.width, points[0].y * size.height); points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }; close() }
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)); addPath(polygon) }, Color.Black.copy(alpha = .5f))
            drawPath(polygon, accent, style = Stroke(3.dp.toPx()))
            points.forEach { drawCircle(accent, 9.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
        }
    }
}

@Composable private fun ScannerAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 46.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = .55f) else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, containerColor = color.copy(alpha = .06f))) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
@Composable private fun ScannerArrow(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp, 46.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = .55f) else MaterialTheme.colorScheme.outline)) {
        Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
    }
}
