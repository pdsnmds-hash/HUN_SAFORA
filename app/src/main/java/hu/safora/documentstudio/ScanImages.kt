package hu.safora.documentstudio

import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScanImages {
    fun decode(file: File, maxSide: Int = 2400): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val raw = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val matrix = Matrix()
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (oriented !== raw) raw.recycle()
        val scale = min(1f, maxSide.toFloat() / max(oriented.width, oriented.height))
        if (scale == 1f) return oriented
        return Bitmap.createScaledBitmap(oriented, max(1, (oriented.width * scale).roundToInt()), max(1, (oriented.height * scale).roundToInt()), true).also { oriented.recycle() }
    }
    fun write(bitmap: Bitmap, target: File) {
        target.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
    }
    fun rotate(source: Bitmap): Bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)

    /** Clockwise convex quadrilateral, normalized to image dimensions. */
    fun validCorners(points: List<PointF>): Boolean {
        if (points.size != 4 || points.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
        return points.indices.all { i ->
            val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > .005f
        }
    }
    fun crop(source: Bitmap, points: List<PointF>): Bitmap {
        require(validCorners(points)) { "Crop corners must form a convex page" }
        val p = points.map { PointF(it.x * (source.width - 1), it.y * (source.height - 1)) }
        fun distance(a: PointF, b: PointF) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).roundToInt()
        val width = max(distance(p[0], p[1]), distance(p[3], p[2])).coerceAtLeast(16)
        val height = max(distance(p[0], p[3]), distance(p[1], p[2])).coerceAtLeast(16)
        val transform = Matrix()
        require(transform.setPolyToPoly(p.flatMap { listOf(it.x, it.y) }.toFloatArray(), 0,
            floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat()), 0, 4))
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(source, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        }
    }
    fun exportPdf(pages: List<File>, target: File) {
        require(pages.isNotEmpty())
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, file ->
                val image = decode(file)
                try {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    val scale = min(571f / image.width, 818f / image.height)
                    val w = image.width * scale; val h = image.height * scale
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(image, null, RectF((595 - w) / 2, (842 - h) / 2, (595 + w) / 2, (842 + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page)
                } finally { image.recycle() }
            }
            target.outputStream().use { pdf.writeTo(it) }
        } catch (e: Exception) { target.delete(); throw e }
        finally { pdf.close() }
    }
}
