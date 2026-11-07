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
    @Synchronized fun flush() {
        evidenceInstance?.flush()
    }
    fun memoryEvidenceStore()=PrivateEvidenceStore(KeyGenerator.getInstance("HmacSHA256").apply {init(256)}.generateKey())
    fun memoryDomainSet() = PrivateDomainSet(KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey())
    fun memoryAllowlist() = memoryDomainSet()
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

    @Synchronized fun setUserRuleSubtree(context:Context,domain:String,feedback:UserFeedback):Boolean {
        val changed=when(feedback) {
            UserFeedback.ALLOW -> { blocklist(context).remove(domain); allowlist(context).addSubtree(domain) }
            UserFeedback.BLOCK -> { allowlist(context).remove(domain); blocklist(context).addSubtree(domain) }
            UserFeedback.NONE -> { val a=allowlist(context).remove(domain); val b=blocklist(context).remove(domain); a||b }
        }
        policy(context).feedback(domain,feedback)
        return changed
    }

    @Synchronized fun store(context: Context): PrivateReputationStore {
        instance?.let { return it }
        val legacy = File(context.filesDir, "learned_trackers.txt")
        val result = try {
            val key = deviceKey()
            val file = File(context.noBackupFilesDir, "learned_trackers.bin")
            PrivateReputationStore(key, file).apply { migrate(legacy) }
        } catch (_: Exception) {
            legacy.delete()
            val key = KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey()
            PrivateReputationStore(key, null)
        }
        instance = result
        return result
    }

    @Synchronized fun allowlist(context: Context): PrivateDomainSet {
        allowlistInstance?.let { return it }
        val file = ruleFile(context, "allowlist")
        val result = runCatching {
            PrivateDomainSet(ruleKey(file, "allowlist"), file)
        }.getOrElse {
            val ephemeral = KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey()
            PrivateDomainSet(ephemeral, null)
        }
        allowlistInstance = result
        return result
    }

    @Synchronized fun blocklist(context: Context): PrivateDomainSet {
        blocklistInstance?.let { return it }
        val file = ruleFile(context, "blocklist")
        val result = runCatching {
            PrivateDomainSet(ruleKey(file, "blocklist"), file)
        }.getOrElse {
            val ephemeral = KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey()
            PrivateDomainSet(ephemeral, null)
        }
        blocklistInstance = result
        return result
    }

    @Synchronized fun clearForTesting() {
        instance = null
        allowlistInstance = null
        blocklistInstance = null
        evidenceInstance = null
        policyInstance = null
    }
}
