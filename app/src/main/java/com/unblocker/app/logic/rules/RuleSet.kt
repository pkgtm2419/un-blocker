package com.unblocker.app.logic.rules

import android.content.Context
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary UBR2 rule matcher using reversed labels and allow-before-block precedence.
 *
 * The backing buffer is shared with its creator and must not be mutated after publication.
 *
 * @param buffer Little-endian binary rule data.
 * @property ruleCount Number of indexed rule entries.
 * @param blobOffset Byte offset of the encoded entry blob.
 */
class RuleSet private constructor(
    private val buffer: ByteBuffer,
    val ruleCount: Int,
    private val blobOffset: Int,
) {
    /**
     * Matches exact, suffix and strict-subdomain rules, checking allow rules first.
     *
     * @param raw Hostname to normalize and search.
     * @return Matching rule, or null for invalid names, empty sets or no match.
     */
    fun match(raw: String): DnsRule? {
        val domain = DomainName.normalize(raw) ?: return null
        if (ruleCount == 0) return null

        val len = domain.length
        val reversed = StringBuilder(len)
        var end = len
        var labelCount = 0
        for (i in len - 1 downTo 0) {
            if (domain[i] == '.') {
                if (reversed.isNotEmpty()) reversed.append('.')
                reversed.append(domain, i + 1, end)
                end = i
                labelCount++
            }
        }
        if (reversed.isNotEmpty()) reversed.append('.')
        reversed.append(domain, 0, end)
        labelCount++

        val fullDomainReversed = reversed.toString()
        val prefixLengths = IntArray(labelCount)
        var pIdx = 0
        for (i in 0 until fullDomainReversed.length) {
            if (fullDomainReversed[i] == '.') {
                prefixLengths[pIdx++] = i
            }
        }
        prefixLengths[pIdx] = fullDomainReversed.length

        // 1. Check ALLOW rules first
        matchAction(RuleAction.ALLOW, fullDomainReversed, prefixLengths, labelCount)?.let { return it }

        // 2. Check BLOCK rules
        return matchAction(RuleAction.BLOCK, fullDomainReversed, prefixLengths, labelCount)
    }

    private fun matchAction(
        action: RuleAction,
        fullDomainReversed: String,
        prefixLengths: IntArray,
        labelCount: Int,
    ): DnsRule? {
        val fullLen = prefixLengths[labelCount - 1]
        // Step 1: Check EXACT on full domain
        findRule(fullDomainReversed, fullLen, action, RuleKind.EXACT)?.let { return it }

        // Step 2: Check SUFFIX on full domain
        findRule(fullDomainReversed, fullLen, action, RuleKind.SUFFIX)?.let { return it }

        // Step 3: Check SUFFIX then WILDCARD on parent domains (strict parents)
        for (i in labelCount - 2 downTo 0) {
            val parentLen = prefixLengths[i]
            findRule(fullDomainReversed, parentLen, action, RuleKind.SUFFIX)?.let { return it }
            findRule(fullDomainReversed, parentLen, action, RuleKind.WILDCARD)?.let { return it }
        }

        return null
    }

    private fun findRule(
        fullKey: CharSequence,
        targetLen: Int,
        targetAction: RuleAction,
        targetKind: RuleKind,
    ): DnsRule? {
        var low = 0
        var high = ruleCount
        while (low < high) {
            val mid = (low + high) ushr 1
            val cmp = compareKeyAt(mid, fullKey, targetLen)
            if (cmp < 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        while (low < ruleCount && compareKeyAt(low, fullKey, targetLen) == 0) {
            val entryPos = getEntryPosition(low)
            val keyLen = buffer.get(entryPos).toInt() and 0xFF
            val flags = buffer.get(entryPos + 1 + keyLen).toInt() and 0xFF
            val action = if ((flags and 1) == 0) RuleAction.ALLOW else RuleAction.BLOCK
            val kind =
                when ((flags shr 1) and 3) {
                    0 -> RuleKind.EXACT
                    1 -> RuleKind.SUFFIX
                    2 -> RuleKind.WILDCARD
                    else -> RuleKind.SUFFIX
                }
            if (action == targetAction && kind == targetKind) {
                val category = if (((flags shr 3) and 3) == 0) BlockingCategory.AD else BlockingCategory.ADULT_CONTENT
                val sourceId = buffer.getShort(entryPos + 2 + keyLen).toInt() and 0xFFFF
                val originalDomain =
                    StringBuilder(targetLen)
                        .apply {
                            var end = targetLen
                            for (i in targetLen - 1 downTo 0) {
                                if (fullKey[i] == '.') {
                                    if (isNotEmpty()) append('.')
                                    append(fullKey, i + 1, end)
                                    end = i
                                }
                            }
                            if (isNotEmpty()) append('.')
                            append(fullKey, 0, end)
                        }.toString()
                return DnsRule(
                    action = action,
                    kind = kind,
                    value = originalDomain,
                    category = category,
                    sourceId = sourceId,
                )
            }
            low++
        }
        return null
    }

    private fun getEntryPosition(index: Int): Int {
        val relOffset = buffer.getInt(14 + index * 4)
        return blobOffset + relOffset
    }

    private fun compareKeyAt(
        index: Int,
        target: CharSequence,
        targetLen: Int,
    ): Int {
        val entryPos = getEntryPosition(index)
        val keyLen = buffer.get(entryPos).toInt() and 0xFF
        val minLen = minOf(keyLen, targetLen)

        for (i in 0 until minLen) {
            val b = (buffer.get(entryPos + 1 + i).toInt() and 0xFF).toChar()
            val c = target[i]
            if (b != c) return b.compareTo(c)
        }
        return keyLen.compareTo(targetLen)
    }

    /**
     * Creates rule matchers from binary data or an empty encoded set.
     */
    companion object {
        /**
         * Reads the UBR2 header and retains a duplicate sharing the supplied storage.
         *
         * @param buffer Encoded UBR2 bytes with header at absolute offset zero.
         * @return Matcher over the shared data.
         * @throws IllegalArgumentException If header, magic, version, count or checked size is invalid.
         */
        fun fromByteBuffer(buffer: ByteBuffer): RuleSet {
            val buf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            require(buf.remaining() >= 14) { "Invalid UBR2 header length" }
            val b0 = buf.get(0).toInt().toChar()
            val b1 = buf.get(1).toInt().toChar()
            val b2 = buf.get(2).toInt().toChar()
            val b3 = buf.get(3).toInt().toChar()
            require(b0 == 'U' && b1 == 'B' && b2 == 'R' && b3 == '2') { "Invalid UBR2 magic header" }
            val version = buf.getShort(4).toInt() and 0xFFFF
            require(version == 2) { "Unsupported UBR2 version: $version" }
            val ruleCount = buf.getInt(6)
            require(ruleCount >= 0) { "Negative ruleCount in UBR2" }
            val blobLen = buf.getInt(10)
            val headerAndOffsetsLen = 14 + ruleCount * 4
            require(buf.remaining() >= headerAndOffsetsLen + blobLen) { "Truncated UBR2 buffer" }
            return RuleSet(buf, ruleCount, headerAndOffsetsLen)
        }

        /**
         * Wraps an encoded UBR2 array without copying it.
         *
         * @param bytes Binary header, offset index and entry blob.
         * @return Matcher sharing the supplied array.
         * @throws IllegalArgumentException If the checked UBR2 header fields are invalid.
         */
        fun fromByteArray(bytes: ByteArray): RuleSet = fromByteBuffer(ByteBuffer.wrap(bytes))

        /**
         * Returns a matcher over an empty valid UBR2 header.
         */
        fun empty(): RuleSet {
            val emptyHeader =
                ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put('U'.code.toByte())
                    put('B'.code.toByte())
                    put('R'.code.toByte())
                    put('2'.code.toByte())
                    putShort(2.toShort())
                    putInt(0)
                    putInt(0)
                    flip()
                }
            return fromByteBuffer(emptyHeader)
        }
    }
}

/**
 * Encodes this collection as sorted UBR2 data and returns a matcher over the new buffer.
 *
 * Source IDs are encoded in 16 bits; non-AD categories use the adult-content encoding.
 *
 * @return Matcher owning the newly allocated encoded buffer.
 * @throws IllegalArgumentException If a reversed hostname key exceeds 255 ASCII bytes.
 */
fun CompiledRuleSet.toRuleSet(): RuleSet {
    if (rules.isEmpty()) return RuleSet.empty()

    data class Entry(
        val rule: DnsRule,
        val revKey: String,
    )
    val sorted =
        rules
            .map { rule ->
                val revKey =
                    rule.value
                        .split('.')
                        .asReversed()
                        .joinToString(".")
                Entry(rule, revKey)
            }.sortedWith(
                compareBy(
                    { it.revKey },
                    { it.rule.action.ordinal },
                    { it.rule.kind.ordinal },
                    { it.rule.category.ordinal },
                    { it.rule.sourceId },
                ),
            )

    val count = sorted.size
    val offsets = IntArray(count)
    val blobOut = ByteArrayOutputStream()

    for (i in 0 until count) {
        val entry = sorted[i]
        val keyBytes = entry.revKey.toByteArray(Charsets.US_ASCII)
        require(keyBytes.size <= 255) { "Key too long: ${entry.revKey}" }

        val actionBit = if (entry.rule.action == RuleAction.ALLOW) 0 else 1
        val kindBits =
            when (entry.rule.kind) {
                RuleKind.EXACT -> 0
                RuleKind.SUFFIX -> 1
                RuleKind.WILDCARD -> 2
            }
        val catBits = if (entry.rule.category == BlockingCategory.AD) 0 else 1
        val flags = actionBit or (kindBits shl 1) or (catBits shl 3)

        offsets[i] = blobOut.size()
        blobOut.write(keyBytes.size)
        blobOut.write(keyBytes)
        blobOut.write(flags)
        blobOut.write(entry.rule.sourceId and 0xFF)
        blobOut.write((entry.rule.sourceId shr 8) and 0xFF)
    }

    val blobBytes = blobOut.toByteArray()
    val totalSize = 14 + count * 4 + blobBytes.size
    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put('U'.code.toByte())
    buffer.put('B'.code.toByte())
    buffer.put('R'.code.toByte())
    buffer.put('2'.code.toByte())
    buffer.putShort(2.toShort())
    buffer.putInt(count)
    buffer.putInt(blobBytes.size)
    for (offset in offsets) {
        buffer.putInt(offset)
    }
    buffer.put(blobBytes)
    buffer.flip()
    return RuleSet.fromByteBuffer(buffer)
}

/**
 * Most recent asset-loading outcome reported by RuleSetHolder.
 */
sealed class RuleSetStatus {
    /**
     * Successfully loaded rules.
     *
     * @property count Number of rules in the loaded set.
     * @property source Asset format identifier.
     */
    data class Loaded(
        val count: Int,
        val source: String,
    ) : RuleSetStatus()

    /**
     * Embedded seed rules selected after a loading failure.
     *
     * @property reason Loading stage or error category that triggered fallback.
     */
    data class Fallback(
        val reason: String,
    ) : RuleSetStatus()

    /**
     * Initial state before any asset-loading result has been published.
     */
    object Failed : RuleSetStatus()
}

/**
 * Process-shared lazy rule holder; successful loads are cached, while fallback loads may be retried.
 */
object RuleSetHolder {
    @Volatile
    private var cached: RuleSet? = null

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<RuleSetStatus>(RuleSetStatus.Failed)

    /**
     * Most recent asset-load result; cache overrides for tests do not update this flow.
     */
    val status: kotlinx.coroutines.flow.StateFlow<RuleSetStatus> = _status

    /**
     * Returns cached rules or loads assets, caching only a non-fallback result.
     *
     * @param context Application asset context.
     * @return Loaded rule matcher or embedded fallback seed matcher.
     */
    fun get(context: Context): RuleSet {
        val current = cached
        if (current != null) return current
        return synchronized(this) {
            cached ?: load(context).also {
                if (it != fallbackRuleSet) {
                    cached = it
                }
            }
        }
    }

    /**
     * Overrides the cached matcher without changing load status.
     *
     * @param ruleSet Replacement matcher for tests.
     */
    fun setForTesting(ruleSet: RuleSet) {
        synchronized(this) { cached = ruleSet }
    }

    /**
     * Drops the cached matcher without changing load status.
     */
    fun clearForTesting() {
        synchronized(this) { cached = null }
    }

    private val fallbackRuleSet: RuleSet by lazy {
        val rules =
            com.unblocker.app.logic.AdDetector.defaultSeedRules.map {
                DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it, com.unblocker.app.domain.model.BlockingCategory.AD, 0)
            }
        val cr = CompiledRuleSet(rules)
        cr.toRuleSet()
    }

    private fun load(context: Context): RuleSet = loadWithOpener { name -> context.assets.open(name) }

    /**
     * Loads the manifest and binary asset, checking SHA-256 when the manifest provides one.
     *
     * @param openAsset Opens a named asset stream; each opened stream is closed by this method.
     * @return Loaded matcher, or embedded seeds after handled manifest, I/O, format or runtime failure.
     */
    fun loadWithOpener(openAsset: (String) -> java.io.InputStream): RuleSet {
        var expectedSha256 = ""
        try {
            openAsset("dns-rules-manifest.json").reader().use { reader ->
                val text = reader.readText()
                val json = org.json.JSONObject(text)
                expectedSha256 = json.optString("binarySha256", "")
            }
        } catch (e: Exception) {
            if (e is VirtualMachineError) throw e
            _status.value = RuleSetStatus.Fallback("manifest")
            return fallbackRuleSet
        }

        try {
            val bytes = openAsset("dns-rules.bin").use { input -> input.readBytes() }
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val actualSha256 = digest.joinToString("") { "%02x".format(it) }

            if (expectedSha256.isNotEmpty() && expectedSha256 != actualSha256) {
                _status.value = RuleSetStatus.Fallback("integrity")
                return fallbackRuleSet
            }
            val ruleSet = RuleSet.fromByteArray(bytes)
            _status.value = RuleSetStatus.Loaded(ruleSet.ruleCount, "bin")
            return ruleSet
        } catch (e: java.io.IOException) {
            _status.value = RuleSetStatus.Fallback("io")
        } catch (e: IllegalArgumentException) {
            _status.value = RuleSetStatus.Fallback("format")
        } catch (e: RuntimeException) {
            _status.value = RuleSetStatus.Fallback("runtime")
        }

        return fallbackRuleSet
    }
}
