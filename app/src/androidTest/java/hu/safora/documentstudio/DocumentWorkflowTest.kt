package hu.safora.documentstudio

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun invoice(file: File): File {
        val image = Bitmap.createBitmap(1400, 1800, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 70f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            drawText("SAFORA INVOICE", 100f, 200f, paint)
            drawText("TOTAL 12345 EUR", 100f, 400f, paint)
            paint.textSize = 44f
            drawText("Payment due 2026-11-30", 100f, 600f, paint)
        }
        ScanImages.write(image, file); image.recycle(); return file
    }
    private fun grant(permission: String) { instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").close() }
    @Test fun perspectiveCropRotationMultipagePdfAndOfflineOcr() = runBlocking {
        val dir = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val raw = invoice(File(dir, "invoice.jpg"))
            val image = ScanImages.decode(raw)
            val points = listOf(PointF(.02f,.02f),PointF(.98f,.02f),PointF(.98f,.98f),PointF(.02f,.98f))
            assertTrue(ScanImages.validCorners(points))
            assertFalse(ScanImages.validCorners(listOf(points[0],points[2],points[1],points[3])))
            val cropped = ScanImages.crop(image, points)
            assertTrue(cropped.width < image.width)
            val rotated = ScanImages.rotate(cropped)
            assertEquals(cropped.width, rotated.height)
            val second = File(dir,"second.jpg");ScanImages.write(rotated,second)
            val first = File(dir,"first.jpg");ScanImages.write(cropped,first)
            image.recycle();cropped.recycle();rotated.recycle()
            val pdf = File(dir,"scan.pdf");ScanImages.exportPdf(listOf(first,second),pdf)
            PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(2,it.pageCount) }
            val text=DocumentOcr.recognize(pdf,"application/pdf")
            assertTrue(text,text.contains("SAFORA",true))
            assertTrue(text,text.contains("12345"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun workerPersistsTextForSearchAndDeletionRemovesIndex() = runBlocking {
        val id=UUID.randomUUID().toString()
        val file=invoice(File(context.filesDir,"documents").apply{mkdirs()}.resolve(id))
        val doc=Doc(id,"photo.jpg",file.path,"Unsorted","image/jpeg",file.length(),System.currentTimeMillis())
        try {
            val worker=TestListenableWorkerBuilder<OcrWorker>(context).setInputData(workDataOf("id" to id,"path" to file.path,"type" to doc.type)).build()
            assertEquals(ListenableWorker.Result.success(workDataOf("empty" to false)),worker.doWork())
            assertTrue(DocumentOcr.index(context,id).exists())
            assertTrue(doc.copy(text=DocumentOcr.text(context,id)).matches("total 12345"))
            DocumentOcr.delete(context,id)
            assertFalse(DocumentOcr.index(context,id).exists())
        } finally { file.delete();DocumentOcr.delete(context,id) }
    }
    @Test fun corruptPdfDoesNotLeaveAnOcrIndex() = runBlocking {
        val id=UUID.randomUUID().toString();val file=File(context.filesDir,"documents").apply{mkdirs()}.resolve(id).apply{writeText("not a PDF")}
        try {
            val worker=TestListenableWorkerBuilder<OcrWorker>(context).setInputData(workDataOf("id" to id,"path" to file.path,"type" to "application/pdf")).build()
            assertTrue(worker.doWork() is ListenableWorker.Result.Failure)
            assertFalse(DocumentOcr.index(context,id).exists())
        } finally { file.delete() }
    }
    @Test fun legacyDeadlineMigrationPreservesOriginalData() {
        val dir=File(context.cacheDir,UUID.randomUUID().toString()).apply{mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getFilesDir()=dir}
        val legacy=File(dir,"safora_data.json")
        val original=JSONObject().put("docs",JSONArray()).put("deadlines",JSONArray().put(JSONObject().put("id","legacy").put("title","Tax").put("date","2026-11-30"))).toString()
        legacy.writeText(original)
        try {
            val migrated=DeadlineRepository(isolated).all().single()
            assertEquals("Tax",migrated.title);assertEquals("09:00",migrated.time)
            assertEquals(original,legacy.readText())
            assertEquals(migrated,DeadlineRepository(isolated).all().single())
        } finally { dir.deleteRecursively() }
    }
    @Test fun notificationWorkerDeliversOnceAndRecurringDeadlineAdvances() = runBlocking {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        ReminderScheduler.channel(context)
        assertTrue(ReminderScheduler.notificationsEnabled(context))
        val repo=DeadlineRepository(context)
        val past=LocalDateTime.now().minusMinutes(2)
        val id=UUID.randomUUID().toString()
        try {
            repo.save(Deadline(id,"Safora notification test",past.toLocalDate().toString(),time=past.toLocalTime().withSecond(0).withNano(0).toString(),leadMinutes=0,repeat="DAILY"))
            val d=repo.all().single{it.id==id}
            val worker=TestListenableWorkerBuilder<ReminderWorker>(context).setInputData(workDataOf("id" to id,"revision" to d.revision,"date" to d.date,"lead" to false)).build()
            assertEquals(ListenableWorker.Result.success(),worker.doWork())
            val notifications=context.getSystemService(NotificationManager::class.java).activeNotifications.filter{it.tag==id}
            assertEquals(1,notifications.size)
            assertEquals("Safora notification test",notifications.single().notification.extras.getString("android.title"))
            val next=repo.all().single{it.id==id}
            assertTrue(DeadlineRules.dueAt(next)>System.currentTimeMillis())
            repo.deliver(id,d.revision,d.date,false)
            assertEquals(next,repo.all().single{it.id==id})
            repo.toggle(id)
            assertTrue(repo.all().single{it.id==id}.done)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.tag==id})
        } finally { repo.delete(id) }
    }
    @Test fun editedAndDeletedRemindersRejectStaleJobs() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        val repo=DeadlineRepository(context);val id=UUID.randomUUID().toString();val future=LocalDateTime.now().plusDays(1)
        try {
            repo.save(Deadline(id,"Old",future.toLocalDate().toString(),time="09:00"))
            val old=repo.all().single{it.id==id}
            repo.save(old.copy(title="New"))
            repo.deliver(id,old.revision,old.date,false,DeadlineRules.dueAt(old)+1000)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.tag==id})
            repo.delete(id);repo.deliver(id,old.revision,old.date,false,DeadlineRules.dueAt(old)+1000)
            assertTrue(repo.all().none{it.id==id})
        } finally { repo.delete(id) }
    }
    @Test fun recognizedTextSearchCopyAndLinkedReminderThroughUi() {
        grant(Manifest.permission.POST_NOTIFICATIONS)
        val originalEnglish=runCatching{JSONObject(File(context.filesDir,"safora_data.json").readText()).optBoolean("english")}.getOrDefault(false)
        val raw=invoice(File(context.cacheDir,"e2e-invoice.jpg"))
        val pdf=File(context.cacheDir,"e2e-invoice.pdf")
        ScanImages.exportPdf(listOf(raw),pdf)
        var docId="";var reminderId=""
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario->
                scenario.onActivity{activity->val store=Store(activity);store.language(true);store.addScannedPdf(pdf,"Acceptance invoice","Unsorted");docId=store.docs.last().id}
                val expires=System.currentTimeMillis()+45_000
                while(!DocumentOcr.index(context,docId).exists() && System.currentTimeMillis()<expires)Thread.sleep(200)
                assertTrue(DocumentOcr.text(context,docId).contains("12345"))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("Documents")),10000))
                device.findObject(By.clickable(true).hasDescendant(By.text("Documents"))).click()
                assertTrue(device.wait(Until.hasObject(By.text("Default vault")),10000))
                device.findObject(By.text("Default vault")).click()
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")),10000))
                device.findObject(By.clazz("android.widget.EditText")).text="12345"
                assertTrue(device.wait(Until.hasObject(By.text("Acceptance invoice.pdf")),10000))
                device.findObject(By.desc("Actions")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Recognize text (OCR)")),5000))
                device.findObject(By.text("Recognize text (OCR)")).click()
                assertTrue(device.wait(Until.hasObject(By.textContains("TOTAL 12345")),10000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"ocr.png"))
                device.findObject(By.text("Copy")).click()
                scenario.onActivity{activity->val clipboard=activity.getSystemService(android.content.ClipboardManager::class.java);assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("12345"))}
                device.findObject(By.text("Close")).click()
                device.findObject(By.desc("Actions")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Add reminder")),5000))
                device.findObject(By.text("Add reminder")).click()
                assertTrue(device.wait(Until.hasObject(By.text("New reminder")),5000))
                device.findObject(By.clazz("android.widget.EditText")).text="Invoice payment acceptance"
                device.takeScreenshot(File(context.getExternalFilesDir(null),"reminder-editor.png"))
                device.findObject(By.text("Save")).click()
                assertTrue(device.wait(Until.gone(By.text("New reminder")),5000))
                val deadline=DeadlineRepository(context).all().single{it.title=="Invoice payment acceptance"}
                reminderId=deadline.id;assertEquals(docId,deadline.documentId)
                device.findObject(By.clickable(true).hasDescendant(By.text("Deadlines"))).click()
                assertTrue(device.wait(Until.hasObject(By.text("Invoice payment acceptance")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"reminders.png"))
                // Simulate due delivery, then verify that tapping notification opens the linked PDF.
                DeadlineRepository(context).deliver(deadline.id,deadline.revision,deadline.date,false,DeadlineRules.dueAt(deadline)+1000)
                val notification=context.getSystemService(NotificationManager::class.java).activeNotifications.single{it.tag==deadline.id}
                notification.notification.contentIntent.send()
                assertTrue(device.wait(Until.hasObject(By.text("Acceptance invoice.pdf")),10000))
                assertTrue(device.wait(Until.hasObject(By.text("1 / 1")),10000))
            }
        } finally {
            if(reminderId.isNotEmpty())DeadlineRepository(context).delete(reminderId)
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->val store=Store(activity);if(docId.isNotEmpty())store.deleteDoc(docId);store.language(originalEnglish)}}
            raw.delete();pdf.delete()
        }
    }
    @Test fun scannerCameraCaptureAndSessionSurviveReopen() {
        val beforeIds=runCatching{JSONObject(File(context.filesDir,"safora_data.json").readText()).optJSONArray("docs")}.getOrNull()?.let{a->(0 until a.length()).map{a.getJSONObject(it).getString("id")}.toSet()}?:emptySet()
        grant(Manifest.permission.CAMERA)
        val session=ScanFiles(context)
        // Tests only run on the dedicated Safora emulator; preserve any existing scan.
        if(session.pages().isNotEmpty())return
        try {
            ActivityScenario.launch<ScannerActivity>(Intent(context,ScannerActivity::class.java).putExtra("english",true)).use {
                assertTrue(device.wait(Until.hasObject(By.clickable(true).enabled(true).hasDescendant(By.text("Capture"))),15000))
                device.waitForIdle()
                device.takeScreenshot(File(context.getExternalFilesDir(null),"scanner-before.png"))
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"scanner-before.xml"))
                device.findObject(By.clickable(true).enabled(true).hasDescendant(By.text("Capture"))).click()
                val captured=device.wait(Until.hasObject(By.text("Crop")),20000)
                device.takeScreenshot(File(context.getExternalFilesDir(null),"scanner-after.png"))
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"scanner-after.xml"))
                assertTrue(captured)
                assertEquals(1,session.pages().size)
                assertTrue(device.wait(Until.hasObject(By.clickable(true).enabled(true).hasDescendant(By.text("Crop"))),10000))
                device.findObject(By.text("Crop")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Apply crop")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"scanner-crop.png"))
                device.findObject(By.text("Apply crop")).click()
                assertTrue(device.wait(Until.hasObject(By.clickable(true).enabled(true).hasDescendant(By.text("Rotate"))),10000))
                device.findObject(By.text("Rotate")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Save PDF & recognize text")),10000))
            }
            ActivityScenario.launch<ScannerActivity>(Intent(context,ScannerActivity::class.java).putExtra("english",true)).use {
                assertTrue(device.wait(Until.hasObject(By.text("Crop")),15000))
                assertEquals(1,session.pages().size)
                device.findObject(By.text("Save PDF & recognize text")).click()
                val expires=System.currentTimeMillis()+15_000
                while(session.pages().isNotEmpty()&&System.currentTimeMillis()<expires)Thread.sleep(100)
                assertTrue(session.pages().isEmpty())
                val stored=JSONObject(File(context.filesDir,"safora_data.json").readText()).getJSONArray("docs")
                val created=(0 until stored.length()).map{stored.getJSONObject(it)}.single{it.getString("id") !in beforeIds}
                PdfRenderer(ParcelFileDescriptor.open(File(created.getString("path")),ParcelFileDescriptor.MODE_READ_ONLY)).use{assertEquals(1,it.pageCount)}
            }
        } finally {
            session.clear()
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->val store=Store(activity);store.docs.filter{it.id !in beforeIds}.forEach{store.deleteDoc(it.id)}}}
        }
    }
}
