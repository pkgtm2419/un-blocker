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
    private var allowlistInstance: PrivateDomainSet? = null
    private var blocklistInstance: PrivateDomainSet? = null
    private var evidenceInstance: PrivateEvidenceStore? = null
    private var policyInstance: ReputationPolicy? = null

    private fun deviceKey(alias: String = ALIAS): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keystore.getKey(alias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKey()
    }

    @Synchronized fun evidenceStore(context: Context): PrivateEvidenceStore {
        evidenceInstance?.let { return it }
        // Old opaque v2 IDs cannot be re-keyed. Reset evidence, never user rules.
        val result = EvidenceStoreInitializer.create(context.filesDir,context.noBackupFilesDir,
            { deviceKey("unblocker.reputation.hmac.v3") },::memoryEvidenceStore)
        evidenceInstance=result
        return result
    }
    @Synchronized fun policy(context:Context):ReputationPolicy = policyInstance ?: ReputationPolicy(evidenceStore(context))
        .also { policyInstance=it }
    fun memoryEvidenceStore()=PrivateEvidenceStore(KeyGenerator.getInstance("HmacSHA256").apply {init(256)}.generateKey())
    private fun ruleFile(context:Context,name:String):File {
        val legacy=File(context.noBackupFilesDir,"$name-v1")
        return if(legacy.exists()) legacy else File(context.noBackupFilesDir,"$name-v3")
    }
    private fun ruleKey(file:File,name:String)=deviceKey(if(file.name.endsWith("-v1")) ALIAS else "unblocker.$name.hmac.v3")
    @Synchronized fun setUserRule(context:Context,domain:String,feedback:UserFeedback):Boolean {
        val changed=when(feedback) {
            UserFeedback.ALLOW -> { blocklist(context).remove(domain); allowlist(context).add(domain) }
            UserFeedback.BLOCK -> { allowlist(context).remove(domain); blocklist(context).add(domain) }
            UserFeedback.NONE -> { val a=allowlist(context).remove(domain); val b=blocklist(context).remove(domain); a||b }
        }
        policy(context).feedback(domain,feedback)
        return changed
    }

    @Synchronized fun store(context: Context): PrivateReputationStore {
        instance?.let { return it }
        val legacy = File(context.filesDir, "learned_trackers.txt")
        val result = try {
            PrivateReputationStore(deviceKey(), File(context.noBackupFilesDir, "learning-v1"))
                .apply { migrate(legacy) }
        } catch (_: Exception) {
            // No plaintext fallback: ephemeral learning if device storage/Keystore is unavailable.
            legacy.delete()
            memoryStore()
        }
        instance = result
        return result
    }

    @Synchronized fun allowlist(context: Context): PrivateDomainSet {
        allowlistInstance?.let { return it }
        val result = try {
            val file=ruleFile(context,"allowlist")
            PrivateDomainSet(ruleKey(file,"allowlist"),file)
        } catch (_: Exception) {
            // No plaintext fallback: exceptions remain memory-only if protected storage fails.
            memoryAllowlist()
        }
        allowlistInstance = result
        return result
    }

    @Synchronized fun blocklist(context: Context): PrivateDomainSet {
        blocklistInstance?.let { return it }
        val result = try {
            val file=ruleFile(context,"blocklist")
            PrivateDomainSet(ruleKey(file,"blocklist"),file)
        } catch (_: Exception) {
            // No plaintext fallback: rules remain memory-only if protected storage fails.
            memoryDomainSet()
        }
        blocklistInstance = result
        return result
    }

    fun memoryStore() = PrivateReputationStore(KeyGenerator.getInstance("HmacSHA256").apply {
        init(256)
    }.generateKey())

    fun memoryDomainSet() = PrivateDomainSet(KeyGenerator.getInstance("HmacSHA256").apply {
        init(256)
    }.generateKey())

    fun memoryAllowlist() = memoryDomainSet()

    @Synchronized fun clear(context: Context) {
        store(context).clear()
        evidenceStore(context).clear()
        // Also remove an older snapshot if this process fell back to a memory-only store.
        listOf(File(context.noBackupFilesDir, "learning-v1"),
            File(context.noBackupFilesDir, "learning-v1.tmp"),
            File(context.noBackupFilesDir,"learning-v3"),File(context.noBackupFilesDir,"learning-v3.tmp"),
            File(context.filesDir, "learned_trackers.txt")).forEach { file ->
            check(!file.exists() || file.delete()) { "Learning cleanup failed" }
        }
    }

    @Synchronized fun clearAllowlist(context: Context) {
        allowlist(context).clear()
        listOf(File(context.noBackupFilesDir, "allowlist-v1"),
            File(context.noBackupFilesDir,"allowlist-v3"),File(context.noBackupFilesDir,"allowlist-v3.tmp"),
            File(context.noBackupFilesDir, "allowlist-v1.tmp")).forEach { file ->
            check(!file.exists() || file.delete()) { "Allowlist cleanup failed" }
        }
    }

    @Synchronized fun clearBlocklist(context: Context) {
        blocklist(context).clear()
        listOf(File(context.noBackupFilesDir, "blocklist-v1"),
            File(context.noBackupFilesDir,"blocklist-v3"),File(context.noBackupFilesDir,"blocklist-v3.tmp"),
            File(context.noBackupFilesDir, "blocklist-v1.tmp")).forEach { file ->
            check(!file.exists() || file.delete()) { "Blocklist cleanup failed" }
        }
    }

    fun clearRules(context: Context) {
        clearAllowlist(context)
        clearBlocklist(context)
        evidenceStore(context).clearFeedback()
    }
}
