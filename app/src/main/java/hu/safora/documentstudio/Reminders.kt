package hu.safora.documentstudio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DeadlineRepository(private val context: Context) {
    companion object { private val lock = Any() }
    private val file get() = AtomicFile(File(context.filesDir, "deadlines.json"))
    fun all(): List<Deadline> = synchronized(lock) { read() }
    private fun read(): List<Deadline> {
        val target = file
        val array = if (target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()) {
            target.openRead().bufferedReader().use { JSONArray(it.readText()) }
        } else {
            val legacy = AtomicFile(File(context.filesDir, "safora_data.json"))
            val previous = if (legacy.baseFile.exists() || File(legacy.baseFile.path + ".bak").exists())
                legacy.openRead().bufferedReader().use { JSONObject(it.readText()).optJSONArray("deadlines") ?: JSONArray() }
            else JSONArray()
            val items = decode(previous)
            write(items)
            return items
        }
        return decode(array)
    }
    private fun decode(array: JSONArray) = List(array.length()) { index ->
        val j = array.getJSONObject(index)
        Deadline(j.getString("id"), j.getString("title"), j.getString("date"), j.optBoolean("done"),
            j.optString("time", "09:00"), j.optString("documentId"), j.optLong("leadMinutes", 1440),
            j.optString("repeat", "NONE"), j.optInt("anchorDay"), j.optString("revision").ifBlank { UUID.randomUUID().toString() },
            j.optString("leadDelivered"), j.optString("dueDelivered"))
    }
    private fun write(items: List<Deadline>) {
        val array = JSONArray().apply { items.forEach { d -> put(JSONObject()
            .put("id", d.id).put("title", d.title).put("date", d.date).put("done", d.done)
            .put("time", d.time).put("documentId", d.documentId).put("leadMinutes", d.leadMinutes)
            .put("repeat", d.repeat).put("anchorDay", d.anchorDay).put("revision", d.revision)
            .put("leadDelivered", d.leadDelivered).put("dueDelivered", d.dueDelivered)) } }
        val atomic = file; val output = atomic.startWrite()
        try { output.write(array.toString().toByteArray()); atomic.finishWrite(output) }
        catch(e: Exception) { atomic.failWrite(output); throw e }
    }
    fun save(deadline: Deadline) = synchronized(lock) {
        require(DeadlineRules.valid(deadline))
        val updated = deadline.copy(title = deadline.title.trim(), revision = UUID.randomUUID().toString(), leadDelivered = "", dueDelivered = "")
        val items = read().filterNot { it.id == deadline.id } + updated
        write(items)
        ReminderScheduler.cancel(context, deadline.id)
        ReminderScheduler.schedule(context, updated)
    }
    fun toggle(id: String) = synchronized(lock) {
        val d = read().find { it.id == id } ?: return@synchronized
        save(d.copy(done = !d.done))
    }
    fun delete(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id }); ReminderScheduler.cancel(context, id)
    }
    fun unlink(documentId: String) = synchronized(lock) { write(read().map { if (it.documentId == documentId) it.copy(documentId = "") else it }) }
    fun reschedule() = synchronized(lock) { read().forEach { ReminderScheduler.schedule(context, it) } }
    fun timeChanged() = synchronized(lock) {
        val items = read().map { it.copy(revision = UUID.randomUUID().toString()) }
        write(items)
        items.forEach { ReminderScheduler.cancel(context, it.id); ReminderScheduler.schedule(context, it) }
    }
    /** Lock covers stale-work validation, delivery and advancing a recurring occurrence. */
    fun deliver(id: String, revision: String, date: String, lead: Boolean, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val items = read(); val d = items.find { it.id == id } ?: return@synchronized
        if (d.done || d.revision != revision || d.date != date || !DeadlineRules.valid(d)) return@synchronized
        val due = DeadlineRules.dueAt(d)
        if (lead && (now >= due || d.leadDelivered == date)) return@synchronized
        if (!lead && d.dueDelivered == date) return@synchronized
        val trigger = due - if (lead) d.leadMinutes * 60_000 else 0
        if (now < trigger) return@synchronized
        if (!ReminderScheduler.notificationsEnabled(context)) return@synchronized
        ReminderScheduler.notify(context, d, lead)
        val updated = if (lead) d.copy(leadDelivered = date) else {
            val next = DeadlineRules.nextDate(d, now)
            if (next == null) d.copy(dueDelivered = date) else d.copy(date = next, leadDelivered = "", dueDelivered = "")
        }
        write(items.map { if (it.id == id) updated else it })
        ReminderScheduler.schedule(context, updated)
    }
}

object ReminderScheduler {
    const val CHANNEL = "safora_deadlines"
    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun channel(context: Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Safora · Deadlines / Határidők", NotificationManager.IMPORTANCE_DEFAULT)) }
    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("deadline:$id")
        context.getSystemService(NotificationManager::class.java).cancel(id, 1)
        context.getSystemService(NotificationManager::class.java).cancel(id, 2)
    }
    fun schedule(context: Context, d: Deadline, now: Long = System.currentTimeMillis()) {
        if (d.done || !DeadlineRules.valid(d)) return
        val due = DeadlineRules.dueAt(d)
        fun enqueue(lead: Boolean, at: Long) {
            val work = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("id" to d.id, "revision" to d.revision, "date" to d.date, "lead" to lead))
                .setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag("reminders").addTag("deadline:${d.id}").build()
            WorkManager.getInstance(context).enqueueUniqueWork("deadline:${d.id}:${d.revision}:${d.date}:$lead", ExistingWorkPolicy.KEEP, work)
        }
        if (d.leadMinutes > 0 && d.leadDelivered != d.date && due - d.leadMinutes * 60_000 > now) enqueue(true, due - d.leadMinutes * 60_000)
        if (d.dueDelivered != d.date) enqueue(false, due)
    }
    @Suppress("MissingPermission")
    fun notify(context: Context, d: Deadline, lead: Boolean) {
        channel(context)
        val settings = AtomicFile(File(context.filesDir, "safora_data.json"))
        val state = runCatching { settings.openRead().bufferedReader().use { JSONObject(it.readText()) } }.getOrNull()
        val english = state?.optBoolean("english") == true
        val linked = state?.optJSONArray("docs")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.find { it.optString("id") == d.documentId } }
        val open = if (linked != null && File(linked.optString("path")).exists()) Intent(context, PreviewActivity::class.java)
            .putExtra("path", linked.getString("path")).putExtra("type", linked.getString("type")).putExtra("title", linked.getString("name"))
            .putExtra("english", english).putExtra("dark", state.optBoolean("dark",true))
        else Intent(context, MainActivity::class.java).putExtra("showDeadlines", true)
        open.data = android.net.Uri.parse("safora://reminder/${d.id}")
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = if (english) "${if (lead) "Upcoming" else "Due"}: ${d.date} ${d.time}" else "${if (lead) "Közeleg" else "Esedékes"}: ${d.date} ${d.time}"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(d.title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        context.getSystemService(NotificationManager::class.java).notify(d.id, if (lead) 1 else 2, notification)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            DeadlineRepository(applicationContext).deliver(inputData.getString("id") ?: return@withContext Result.failure(),
                inputData.getString("revision") ?: return@withContext Result.failure(), inputData.getString("date") ?: return@withContext Result.failure(), inputData.getBoolean("lead", false))
            Result.success()
        } catch(e: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
class ReminderTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val result = goAsync()
        java.util.concurrent.Executors.newSingleThreadExecutor().also { executor -> executor.execute {
            try { DeadlineRepository(context).timeChanged() } finally { result.finish(); executor.shutdown() }
        } }
    }
}
