package com.unblocker.app.logic.rules

import android.content.Context
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Immutable, memory-efficient rule set matching engine backed by compact UBR2 binary data.
 * Employs reversed-label binary search to provide prefix lookup over suffix domains.
 * Allocates at most one reversed domain buffer per match invocation.
 */
class RuleSet private constructor(
    private val buffer: ByteBuffer,
    val ruleCount: Int,
    private val blobOffset: Int
) {
    /**
     * Matches domain against rules using semantics identical to DnsRuleEngine:
     * - ALLOW rules are checked before BLOCK rules.
     * - EXACT matches only the exact full domain.
     * - SUFFIX matches the full domain or any parent domain.
     * - WILDCARD matches strict parent domains only (never the domain itself).
     */
    fun match(raw: String): DnsRule? {
        val domain = DomainName.normalize(raw) ?: return null
        if (ruleCount == 0) return null

        val labels = domain.split('.')
        val labelCount = labels.size
        val reversedLabels = labels.asReversed()
        val prefixes = Array(labelCount) { i ->
            reversedLabels.subList(0, i + 1).joinToString(".")
        }
        val fullDomainReversed = prefixes[labelCount - 1]

        // 1. Check ALLOW rules first
        matchAction(RuleAction.ALLOW, fullDomainReversed, prefixes, labelCount)?.let { return it }

        // 2. Check BLOCK rules
        return matchAction(RuleAction.BLOCK, fullDomainReversed, prefixes, labelCount)
    }

    private fun matchAction(
        action: RuleAction,
        fullDomainReversed: String,
        prefixes: Array<String>,
        labelCount: Int
    ): DnsRule? {
        // Step 1: Check EXACT on full domain
        findRule(fullDomainReversed, action, RuleKind.EXACT)?.let { return it }

        // Step 2: Check SUFFIX on full domain
        findRule(fullDomainReversed, action, RuleKind.SUFFIX)?.let { return it }

        // Step 3: Check SUFFIX then WILDCARD on parent domains (strict parents)
        for (i in labelCount - 2 downTo 0) {
            val parentKey = prefixes[i]
            findRule(parentKey, action, RuleKind.SUFFIX)?.let { return it }
            findRule(parentKey, action, RuleKind.WILDCARD)?.let { return it }
        }

        return null
    }

    private fun findRule(key: String, targetAction: RuleAction, targetKind: RuleKind): DnsRule? {
        var low = 0
        var high = ruleCount
        while (low < high) {
            val mid = (low + high) ushr 1
            val cmp = compareKeyAt(mid, key)
            if (cmp < 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        while (low < ruleCount && compareKeyAt(low, key) == 0) {
            val entryPos = getEntryPosition(low)
            val keyLen = buffer.get(entryPos).toInt() and 0xFF
            val flags = buffer.get(entryPos + 1 + keyLen).toInt() and 0xFF
            val action = if ((flags and 1) == 0) RuleAction.ALLOW else RuleAction.BLOCK
            val kind = when ((flags shr 1) and 3) {
                0 -> RuleKind.EXACT
                1 -> RuleKind.SUFFIX
                2 -> RuleKind.WILDCARD
                else -> RuleKind.SUFFIX
            }
            if (action == targetAction && kind == targetKind) {
                val category = if (((flags shr 3) and 3) == 0) BlockingCategory.AD else BlockingCategory.ADULT_CONTENT
                val sourceId = buffer.getShort(entryPos + 2 + keyLen).toInt() and 0xFFFF
                val originalDomain = key.split('.').asReversed().joinToString(".")
                return DnsRule(
                    action = action,
                    kind = kind,
                    value = originalDomain,
                    category = category,
                    sourceId = sourceId
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

    private fun compareKeyAt(index: Int, target: String): Int {
        val entryPos = getEntryPosition(index)
        val keyLen = buffer.get(entryPos).toInt() and 0xFF
        val targetLen = target.length
        val minLen = minOf(keyLen, targetLen)

        for (i in 0 until minLen) {
            val b = (buffer.get(entryPos + 1 + i).toInt() and 0xFF).toChar()
            val c = target[i]
            if (b != c) return b.compareTo(c)
        }
        return keyLen.compareTo(targetLen)
    }

    companion object {
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

        fun fromByteArray(bytes: ByteArray): RuleSet =
            fromByteBuffer(ByteBuffer.wrap(bytes))

        fun empty(): RuleSet {
            val emptyHeader = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).apply {
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
 * Builds an immutable [RuleSet] from this [CompiledRuleSet].
 */
fun CompiledRuleSet.toRuleSet(): RuleSet {
    if (rules.isEmpty()) return RuleSet.empty()

    data class Entry(val rule: DnsRule, val revKey: String)
    val sorted = rules.map { rule ->
        val revKey = rule.value.split('.').asReversed().joinToString(".")
        Entry(rule, revKey)
    }.sortedWith(compareBy(
        { it.revKey },
        { it.rule.action.ordinal },
        { it.rule.kind.ordinal },
        { it.rule.category.ordinal },
        { it.rule.sourceId }
    ))

    val count = sorted.size
    val offsets = IntArray(count)
    val blobOut = ByteArrayOutputStream()

    for (i in 0 until count) {
        val entry = sorted[i]
        val keyBytes = entry.revKey.toByteArray(Charsets.US_ASCII)
        require(keyBytes.size <= 255) { "Key too long: ${entry.revKey}" }

        val actionBit = if (entry.rule.action == RuleAction.ALLOW) 0 else 1
        val kindBits = when (entry.rule.kind) {
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
 * Process-wide thread-safe lazy holder for the compiled [RuleSet].
 * Shared between UnblockerVpnService and HealthCheckService.
 */
object RuleSetHolder {
    @Volatile
    private var cached: RuleSet? = null

    fun get(context: Context): RuleSet {
        return cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
    }

    fun setForTesting(ruleSet: RuleSet) {
        synchronized(this) { cached = ruleSet }
    }

    fun clearForTesting() {
        synchronized(this) { cached = null }
    }

    private fun load(context: Context): RuleSet {
        // 1. Try mmap via openFd (zero-copy from APK uncompressed asset)
        try {
            context.assets.openFd("dns-rules.bin").use { fd ->
                val channel = java.io.FileInputStream(fd.fileDescriptor).channel
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
                return RuleSet.fromByteBuffer(buffer)
            }
        } catch (_: Throwable) {
            // openFd may fail on compressed assets or certain environments
        }

        // 2. Try direct stream for dns-rules.bin
        try {
            context.assets.open("dns-rules.bin").use { input ->
                val bytes = input.readBytes()
                return RuleSet.fromByteArray(bytes)
            }
        } catch (_: Throwable) {
            // Fallback to TSV
        }

        // 3. Fallback to dns-rules.tsv
        return try {
            context.assets.open("dns-rules.tsv").reader().use { reader ->
                CompiledRuleSet.fromTsv(reader).toRuleSet()
            }
        } catch (_: Throwable) {
            RuleSet.empty()
        }
    }
}
