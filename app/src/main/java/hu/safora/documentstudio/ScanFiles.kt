package hu.safora.documentstudio

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Ordered, private scan session survives rotation and process death. */
class ScanFiles(context: Context, sessionKey: String = "Unsorted") {
    companion object { private val migrationLock = Any() }
    private val dir: File = synchronized(migrationLock) {
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(sessionKey.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(File(context.filesDir, "scan-sessions").apply { mkdirs() }, key)
        val legacy = File(context.filesDir, "scan-session")
        val owner = runCatching { File(legacy, "vault.txt").readText() }.getOrDefault("Unsorted")
        // A legacy draft is moved only when opening its own vault. Other vaults never inherit it.
        if (!target.exists() && legacy.isDirectory && owner == sessionKey) {
            check(legacy.renameTo(target)) { "Could not migrate the existing scan" }
        }
        target.apply { check(isDirectory || mkdirs()) { "Could not create scan session" } }
    }
    private val manifest = AtomicFile(File(dir, "pages.json"))
    fun pages(): List<File> = if (manifest.baseFile.exists() || File(manifest.baseFile.path + ".bak").exists())
        manifest.openRead().bufferedReader().use { reader ->
            val array = JSONArray(reader.readText())
            List(array.length()) { File(dir, array.getString(it)) }.filter { it.isFile && it.canonicalFile.parentFile == dir.canonicalFile }
        } else emptyList()
    fun owner():String = runCatching { File(dir,"vault.txt").readText() }.getOrDefault("Unsorted")
    fun setOwner(name:String) { File(dir,"vault.txt").writeText(name) }
    fun newFile() = File(dir, "${UUID.randomUUID()}.jpg")
    fun save(pages: List<File>) {
        require(pages.size <= 30)
        val stream = manifest.startWrite()
        try { stream.write(JSONArray(pages.map { it.name }).toString().toByteArray()); manifest.finishWrite(stream) }
        catch (e: Exception) { manifest.failWrite(stream); throw e }
        val keep = pages.map { it.name }.toSet()
        dir.listFiles()?.filter { it.extension == "jpg" && it.name !in keep }?.forEach { it.delete() }
    }
    fun clear() { dir.deleteRecursively() }
}

