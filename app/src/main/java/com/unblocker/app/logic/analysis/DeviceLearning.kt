package com.unblocker.app.logic.analysis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object DeviceLearning {
    private const val ALIAS = "unblocker.learning.hmac.v1"
    private var instance: PrivateReputationStore? = null

    @Synchronized fun store(context: Context): PrivateReputationStore {
        instance?.let { return it }
        val legacy = File(context.filesDir, "learned_trackers.txt")
        val result = try {
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (keystore.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256).build())
                }.generateKey()
            PrivateReputationStore(key, File(context.noBackupFilesDir, "learning-v1"))
                .apply { migrate(legacy) }
        } catch (_: Exception) {
            // No plaintext fallback: ephemeral learning if device storage/Keystore is unavailable.
            legacy.delete()
            memoryStore()
        }
        instance = result
        return result
    }

    fun memoryStore() = PrivateReputationStore(KeyGenerator.getInstance("HmacSHA256").apply {
        init(256)
    }.generateKey())

    @Synchronized fun clear(context: Context) {
        store(context).clear()
        // Also remove an older snapshot if this process fell back to a memory-only store.
        listOf(File(context.noBackupFilesDir, "learning-v1"),
            File(context.noBackupFilesDir, "learning-v1.tmp"),
            File(context.filesDir, "learned_trackers.txt")).forEach { file ->
            check(!file.exists() || file.delete()) { "Learning cleanup failed" }
        }
    }
}
