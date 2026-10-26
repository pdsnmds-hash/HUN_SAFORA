package hu.safora.documentstudio

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App access verifier only; this does not encrypt document bytes. */
data class VaultPassword(val salt: String, val verifier: String, val iterations: Int = 210_000)
object VaultPasswords {
    fun create(password: CharArray): VaultPassword {
        require(password.size >= 6) { "Use at least 6 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return VaultPassword(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derive(password, salt, 210_000)))
    }
    fun verify(password: CharArray, record: VaultPassword): Boolean = runCatching {
        require(record.iterations in 100_000..1_000_000)
        MessageDigest.isEqual(Base64.getDecoder().decode(record.verifier), derive(password, Base64.getDecoder().decode(record.salt), record.iterations))
    }.getOrDefault(false)
    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
object VaultSessions {
    var unlocked by mutableStateOf(emptySet<String>()); private set
    private val failures = mutableMapOf<String, Int>()
    private val blocked = mutableMapOf<String, Long>()
    @Synchronized fun open(name: String) { unlocked = unlocked + name; failures.remove(name); blocked.remove(name) }
    @Synchronized fun lock(name: String) { unlocked = unlocked - name }
    @Synchronized fun lockAll() { unlocked = emptySet() }
    @Synchronized fun waitSeconds(name: String): Long = ((blocked.getOrDefault(name, 0) - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
    @Synchronized fun failed(name: String) {
        val count = failures.getOrDefault(name, 0) + 1
        failures[name] = count
        if (count >= 5) { blocked[name] = android.os.SystemClock.elapsedRealtime() + 30_000; failures[name] = 0 }
    }
}
class SaforaApplication : Application(), DefaultLifecycleObserver {
    override fun onCreate() { super<Application>.onCreate(); ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    override fun onStop(owner: LifecycleOwner) { VaultSessions.lockAll() }
}
