package hu.safora.documentstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Private activity: paths are only supplied by the app's document library. */
class PreviewActivity : ComponentActivity() {
    private lateinit var store:Store
    override fun onResume(){super.onResume();if(::store.isInitialized)store.refresh()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(intent.getStringExtra("path") ?: run { finish(); return })
        val root = File(filesDir, "documents").canonicalFile
        if (file.canonicalFile.parentFile != root) { finish(); return }
        store=Store(this)
        val document=store.docs.find{runCatching{File(it.path).canonicalFile==file.canonicalFile}.getOrDefault(false)} ?: run{finish();return}
        val english = store.english
        val dark = store.dark
        val pdf = document.type == "application/pdf"
        setContent {
            MaterialTheme(colorScheme = saforaColorScheme(dark),typography=responsiveTypography()) {
                SaforaSystemBars(dark)
                val current=store.docs.find{it.id==document.id}
                if(current==null){LaunchedEffect(Unit){finish()};return@MaterialTheme}
                VaultAccessScreen(store,current.folder,onExit={finish()}) {
                SecureVaultWindow(store.hasVaultPassword(current.folder))
                var page by rememberSaveable { mutableIntStateOf(0) }
                var pages by remember { mutableIntStateOf(1) }
                var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                var error by remember { mutableStateOf(false) }
                var zoom by remember(page) { mutableFloatStateOf(1f) }
                var offsetX by remember(page) { mutableFloatStateOf(0f) }
                var offsetY by remember(page) { mutableFloatStateOf(0f) }
                LaunchedEffect(page) {
                    error = false
                    bitmap = null
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (pdf) {
                                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                                    val count = renderer.pageCount
                                    require(count > 0)
                                    renderer.openPage(page.coerceIn(0, count - 1)).use { source ->
                                        val width = 1400
                                        val height = (width.toLong() * source.height / source.width).coerceIn(1, 2800).toInt()
                                        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        image.eraseColor(AndroidColor.WHITE)
                                        source.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        image to count
                                    }
                                }
                            } else {
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(file.path, bounds)
                                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                                var sample = 1
                                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                                val image = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                                requireNotNull(image) to 1
                            }
                        }
                    }
                    result.onSuccess { (image, count) -> bitmap = image; pages = count }.onFailure { error = true }
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { finish() }) { Text(if (english) "Back" else "Vissza") }
                            Text(current.name, Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { zoom = 1f; offsetX = 0f; offsetY = 0f }) { Text("1:1") }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (error) Text(if (english) "Cannot open this file. It may be damaged or password protected." else "A fájl nem nyitható meg. Lehet, hogy sérült vagy jelszóval védett.", Modifier.padding(24.dp))
                            else bitmap?.let { image ->
                                Image(image.asImageBitmap(), intent.getStringExtra("title"), Modifier.fillMaxSize()
                                    .pointerInput(page) { detectTransformGestures { _, pan, scale, _ ->
                                        zoom = (zoom * scale).coerceIn(1f, 5f)
                                        offsetX = if (zoom == 1f) 0f else (offsetX + pan.x).coerceIn(-size.width * zoom / 2, size.width * zoom / 2)
                                        offsetY = if (zoom == 1f) 0f else (offsetY + pan.y).coerceIn(-size.height * zoom / 2, size.height * zoom / 2)
                                    } }
                                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offsetX; translationY = offsetY })
                            } ?: CircularProgressIndicator()
                        }
                        if (pdf) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { page-- }, enabled = page > 0) { Text(if (english) "Previous" else "Előző") }
                            Text("${page + 1} / $pages")
                            TextButton(onClick = { page++ }, enabled = page + 1 < pages) { Text(if (english) "Next" else "Következő") }
                        }
                    }
                }
                }
            }
        }
    }
}
