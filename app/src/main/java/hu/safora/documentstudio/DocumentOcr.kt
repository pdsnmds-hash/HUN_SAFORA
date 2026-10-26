package hu.safora.documentstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.work.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

fun normalizedSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
fun Doc.matches(query: String): Boolean = normalizedSearch("$name\n$text").contains(normalizedSearch(query.trim()))

object DocumentOcr {
    private val recognitionLock = Mutex()
    private fun directory(context: Context) = File(context.filesDir, "ocr").apply { mkdirs() }
    fun index(context: Context, id: String) = File(directory(context), "$id.txt")
    fun text(context: Context, id: String): String = runCatching { index(context, id).readText() }.getOrDefault("")
    fun enqueue(context: Context, doc: Doc, replace: Boolean = false) {
        if (!replace && index(context, doc.id).exists()) return
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf("id" to doc.id, "path" to doc.path, "type" to doc.type))
            .addTag("ocr").addTag("ocr:${doc.id}").build()
        WorkManager.getInstance(context).enqueueUniqueWork("ocr:${doc.id}", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun delete(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("ocr:$id")
        index(context, id).delete()
        File(directory(context), "$id.txt.new").delete()
    }
    suspend fun recognize(file: File, type: String, progress: suspend (Int, Int) -> Unit = { _, _ -> }): String = withContext(Dispatchers.IO) { recognitionLock.withLock {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = StringBuilder()
        try {
            suspend fun read(image: Bitmap) {
                coroutineContext.ensureActive()
                try {
                    val text = withContext(NonCancellable) { recognizer.process(InputImage.fromBitmap(image, 0)).await().text }
                    if (result.isNotEmpty()) result.append("\n\n")
                    result.append(text)
                    require(result.length <= 4_000_000) { "Document text exceeds OCR index limit" }
                } finally { image.recycle() }
            }
            if (type == "application/pdf") {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    require(renderer.pageCount > 0)
                    for (i in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        renderer.openPage(i).use { page ->
                            val scale = 2200f / max(page.width, page.height)
                            val image = Bitmap.createBitmap(max(1, (page.width * scale).roundToInt()), max(1, (page.height * scale).roundToInt()), Bitmap.Config.ARGB_8888)
                            image.eraseColor(AndroidColor.WHITE)
                            try { page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                            catch(e: Exception) { image.recycle(); throw e }
                            read(image)
                        }
                        progress(i + 1, renderer.pageCount)
                    }
                }
            } else { read(ScanImages.decode(file)); progress(1, 1) }
            result.toString()
        } finally { recognizer.close() }
    } }
}

class OcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val file = File(inputData.getString("path") ?: return@withContext Result.failure())
        val root = File(applicationContext.filesDir, "documents").canonicalFile
        if (file.canonicalFile.parentFile != root || !file.exists()) return@withContext Result.failure()
        try {
            val text = DocumentOcr.recognize(file, inputData.getString("type") ?: "") { page, total -> setProgress(workDataOf("page" to page, "total" to total)) }
            coroutineContext.ensureActive()
            // Deletion wins over an in-flight recognition task.
            if (!file.exists()) return@withContext Result.failure()
            val output = AtomicFile(DocumentOcr.index(applicationContext, id))
            val stream = output.startWrite()
            try { stream.write(text.toByteArray()); output.finishWrite(stream) }
            catch (e: Exception) { output.failWrite(stream); throw e }
            if (!file.exists()) { DocumentOcr.index(applicationContext, id).delete(); return@withContext Result.failure() }
            Result.success(workDataOf("empty" to text.isBlank()))
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { Result.failure(workDataOf("error" to "Unreadable document or recognition failed")) }
    }
}
