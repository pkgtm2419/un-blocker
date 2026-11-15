package com.unblocker.app.logic.analysis

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Owns process-shared private rule and reputation stores backed by Android Keystore where available.
 */
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

    /**
     * Returns the shared v3 evidence store after obsolete evidence cleanup.
     *
     * @param context Application storage context.
     * @return Device-keyed store, with a volatile fallback if opening the new store fails.
     * @throws java.io.IOException If obsolete learning files cannot be removed.
     */
    @Synchronized fun evidenceStore(context: Context): PrivateEvidenceStore {
        evidenceInstance?.let { return it }
        // Old opaque v2 IDs cannot be re-keyed. Reset evidence, never user rules.
        val result = EvidenceStoreInitializer.create(context.filesDir,context.noBackupFilesDir,
            { deviceKey("unblocker.reputation.hmac.v3") },::memoryEvidenceStore)
        evidenceInstance=result
        return result
    }
    /**
     * Returns the shared reputation policy.
     *
     * @param context Application storage context used to initialize evidence.
     * @return Policy over the shared evidence store.
     */
    @Synchronized fun policy(context:Context):ReputationPolicy = policyInstance ?: ReputationPolicy(evidenceStore(context))
        .also { policyInstance=it }
    /**
     * Flushes the shared evidence store if it has been initialized.
     */
    @Synchronized fun flush() {
        evidenceInstance?.flush()
    }
    /**
     * Creates an independent volatile evidence store with a freshly generated HMAC key.
     */
    fun memoryEvidenceStore()=PrivateEvidenceStore(KeyGenerator.getInstance("HmacSHA256").apply {init(256)}.generateKey())
    /**
     * Creates an independent volatile domain set with a freshly generated HMAC key.
     */
    fun memoryDomainSet() = PrivateDomainSet(KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey())
    /**
     * Creates an independent volatile domain set for allow-rule callers.
     */
    fun memoryAllowlist() = memoryDomainSet()
    private fun ruleFile(context:Context,name:String):File {
        val legacy=File(context.noBackupFilesDir,"$name-v1")
        return if(legacy.exists()) legacy else File(context.noBackupFilesDir,"$name-v3")
    }
    private fun ruleKey(file:File,name:String)=deviceKey(if(file.name.endsWith("-v1")) ALIAS else "unblocker.$name.hmac.v3")
    /**
     * Updates an exact user rule, removes the opposite rule and synchronizes reputation feedback.
     *
     * @param context Application storage context.
     * @param domain Hostname to normalize in the private rule sets.
     * @param feedback ALLOW or BLOCK selects the target set; NONE removes both scopes.
     * @return Target insertion result, or whether either removal succeeded for NONE; persistence is best effort.
     */
    @Synchronized fun setUserRule(context:Context,domain:String,feedback:UserFeedback):Boolean {
        val changed=when(feedback) {
            UserFeedback.ALLOW -> { blocklist(context).remove(domain); allowlist(context).add(domain) }
            UserFeedback.BLOCK -> { allowlist(context).remove(domain); blocklist(context).add(domain) }
            UserFeedback.NONE -> { val a=allowlist(context).remove(domain); val b=blocklist(context).remove(domain); a||b }
        }
        policy(context).feedback(domain,feedback)
        return changed
    }

    /**
     * Updates a whole-site user rule and synchronizes reputation feedback.
     *
     * @param context Application storage context.
     * @param domain Hostname whose subtree is selected.
     * @param feedback ALLOW or BLOCK selects the target set; NONE removes both scopes.
     * @return Target insertion result, or whether either removal succeeded for NONE; persistence is best effort.
     */
    @Synchronized fun setUserRuleSubtree(context:Context,domain:String,feedback:UserFeedback):Boolean {
        val changed=when(feedback) {
            UserFeedback.ALLOW -> { blocklist(context).remove(domain); allowlist(context).addSubtree(domain) }
            UserFeedback.BLOCK -> { allowlist(context).remove(domain); blocklist(context).addSubtree(domain) }
            UserFeedback.NONE -> { val a=allowlist(context).remove(domain); val b=blocklist(context).remove(domain); a||b }
        }
        policy(context).feedback(domain,feedback)
        return changed
    }

    /**
     * Returns the legacy score store, migrating readable legacy learning when possible.
     *
     * @param context Application storage context.
     * @return Shared device-keyed score store, or a volatile store after initialization failure.
     */
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

    /**
     * Returns the shared private allow-rule set.
     *
     * @param context Application storage context.
     * @return Persistent set when initialization succeeds, otherwise a volatile set.
     */
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

    /**
     * Returns the shared private block-rule set.
     *
     * @param context Application storage context.
     * @return Persistent set when initialization succeeds, otherwise a volatile set.
     */
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

    /**
     * Drops cached store references for tests; does not delete files or close existing stores.
     */
    @Synchronized fun clearForTesting() {
        instance = null
        allowlistInstance = null
        blocklistInstance = null
        evidenceInstance = null
        policyInstance = null
    }
}
