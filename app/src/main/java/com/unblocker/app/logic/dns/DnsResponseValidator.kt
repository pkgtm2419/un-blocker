package com.unblocker.app.logic.dns

import com.unblocker.app.logic.DomainName

data class DnsResponseMetadata(
    val transactionId: Int,
    val questionName: String,
    val questionType: Int,
    val questionClass: Int,
    val truncated: Boolean,
    val rcode: Int,
    val aliases: List<String>,
    val minPositiveTtlSeconds: Long?,
    val ttlOffsets: List<Int> = emptyList()
)

object DnsResponseValidator {
    private const val DNS_HEADER_LENGTH = 12
    private const val TYPE_CNAME = 5
    private const val MAX_POINTER_HOPS = 128

    fun parseAndValidate(payload: ByteArray, length: Int, query: DnsQuery): DnsResponseMetadata? {
        val metadata = parseMetadata(payload, length) ?: return null
        if (metadata.transactionId != (query.transactionId.toInt() and 0xffff)) return null
        if (metadata.questionName != query.domain) return null
        if (metadata.questionType != (query.queryType.toInt() and 0xffff)) return null
        if (metadata.questionClass != (query.queryClass.toInt() and 0xffff)) return null
        return metadata
    }

    internal fun parseMetadata(payload: ByteArray, length: Int): DnsResponseMetadata? {
        if (length !in DNS_HEADER_LENGTH..payload.size) return null
        val flags = u16(payload, 2)
        if (flags and 0x8000 == 0 || flags and 0x7800 != 0) return null
        if (u16(payload, 4) != 1) return null

        val answerCount = u16(payload, 6)
        val authorityCount = u16(payload, 8)
        val additionalCount = u16(payload, 10)
        val question = decodeName(payload, DNS_HEADER_LENGTH, length) ?: return null
        if (question.name.isEmpty() || question.nextOffset + 4 > length) return null
        val questionType = u16(payload, question.nextOffset)
        val questionClass = u16(payload, question.nextOffset + 2)
        var position = question.nextOffset + 4
        val truncated = flags and 0x0200 != 0

        if (truncated) {
            return DnsResponseMetadata(
                transactionId = u16(payload, 0),
                questionName = question.name,
                questionType = questionType,
                questionClass = questionClass,
                truncated = true,
                rcode = flags and 0x000f,
                aliases = emptyList(),
                minPositiveTtlSeconds = null
            )
        }

        val edges = mutableMapOf<String, String>()
        val ttlOffsets = mutableListOf<Int>()
        var minimumTtl: Long? = null
        var hasZeroAnswerTtl = false
        var optSeen = false
        var extendedRcode = 0
        repeat(answerCount + authorityCount + additionalCount) { recordIndex ->
            val recordName = decodeName(payload, position, length) ?: return null
            position = recordName.nextOffset
            if (position + 10 > length) return null
            val type = u16(payload, position)
            val recordClass = u16(payload, position + 2)
            val ttl = u32(payload, position + 4)
            val dataLength = u16(payload, position + 8)
            val dataOffset = position + 10
            val dataEnd = dataOffset + dataLength
            if (dataEnd < dataOffset || dataEnd > length) return null
            if ((type == 1 && dataLength != 4) || (type == 28 && dataLength != 16)) return null
            if (type != 41) ttlOffsets += position + 4
            if (type == 41) {
                if (recordIndex < answerCount + authorityCount || optSeen || recordName.name.isNotEmpty() ||
                    payload[position+5].toInt()!=0 || !DnsPacketUtil.validEdnsOptions(payload,dataOffset,dataEnd)) return null
                optSeen=true
                extendedRcode=payload[position+4].toInt() and 255
            }
            var bindingTarget: String? = null
            if (type == 64 || type == 65) {
                if (dataLength < 3) return null
                val priority=u16(payload,dataOffset)
                val target=decodeName(payload,dataOffset+2,dataEnd,allowCompression=false) ?: return null
                var parameter=target.nextOffset
                var lastKey=-1
                while(parameter<dataEnd) {
                    if(parameter+4>dataEnd) return null
                    val key=u16(payload,parameter)
                    val size=u16(payload,parameter+2)
                    if(key<=lastKey || parameter+4+size>dataEnd) return null
                    lastKey=key
                    parameter+=4+size
                }
                if(priority==0 && target.name.isNotEmpty() && type==questionType) bindingTarget=target.name
            }

            if (recordIndex < answerCount) {
                if (ttl == 0L) hasZeroAnswerTtl = true
                else minimumTtl = minimumTtl?.coerceAtMost(ttl) ?: ttl
                if (type == TYPE_CNAME) {
                    val alias = decodeName(payload, dataOffset, length) ?: return null
                    if (alias.nextOffset != dataEnd || alias.name.isEmpty()) return null
                    if (recordClass == questionClass) {
                        val old = edges.put(recordName.name, alias.name)
                        if (old != null && old != alias.name) return null
                    }
                }
            }
            if (recordIndex < answerCount && bindingTarget != null && recordClass == questionClass) {
                val old=edges.put(recordName.name,bindingTarget)
                if(old!=null && old!=bindingTarget) return null
            }
            position = dataEnd
        }
        if (position != length) return null

        val aliases = mutableListOf<String>()
        val visited = mutableSetOf(question.name)
        var owner = question.name
        while (edges.containsKey(owner)) {
            if (aliases.size >= 8) return null
            val target = edges.getValue(owner)
            if (!visited.add(target)) return null
            aliases += target
            owner = target
        }

        return DnsResponseMetadata(
            transactionId = u16(payload, 0),
            questionName = question.name,
            questionType = questionType,
            questionClass = questionClass,
            truncated = false,
            rcode = (extendedRcode shl 4) or (flags and 0x000f),
            aliases = aliases,
            minPositiveTtlSeconds = if (hasZeroAnswerTtl) null else minimumTtl,
            ttlOffsets = ttlOffsets
        )
    }

    private data class DecodedName(val name: String, val nextOffset: Int)

    private fun decodeName(payload: ByteArray, start: Int, end: Int, allowCompression: Boolean = true): DecodedName? {
        if (start !in 0 until end) return null
        val labels = mutableListOf<String>()
        val visitedPointers = mutableSetOf<Int>()
        var position = start
        var nextOffset = -1
        var pointerHops = 0
        var expandedLength = 0

        while (position < end) {
            val labelLength = payload[position].toInt() and 0xff
            when {
                labelLength == 0 -> {
                    if (nextOffset < 0) nextOffset = position + 1
                    return DecodedName(labels.joinToString("."), nextOffset)
                }
                labelLength and 0xc0 == 0xc0 -> {
                    if (!allowCompression) return null
                    if (position + 1 >= end) return null
                    val pointer = ((labelLength and 0x3f) shl 8) or
                        (payload[position + 1].toInt() and 0xff)
                    if (pointer >= end || !visitedPointers.add(pointer)) return null
                    pointerHops++
                    if (pointerHops > MAX_POINTER_HOPS) return null
                    if (nextOffset < 0) nextOffset = position + 2
                    position = pointer
                }
                labelLength and 0xc0 != 0 || labelLength > 63 -> return null
                position + 1 + labelLength > end -> return null
                else -> {
                    val label = DomainName.canonicalWireLabel(payload, position + 1, labelLength) ?: return null
                    expandedLength += labelLength + if (labels.isEmpty()) 0 else 1
                    if (expandedLength > 253) return null
                    labels += label
                    position += 1 + labelLength
                }
            }
        }
        return null
    }

    private fun u16(payload: ByteArray, offset: Int): Int =
        ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)

    private fun u32(payload: ByteArray, offset: Int): Long =
        ((payload[offset].toLong() and 0xff) shl 24) or
            ((payload[offset + 1].toLong() and 0xff) shl 16) or
            ((payload[offset + 2].toLong() and 0xff) shl 8) or
            (payload[offset + 3].toLong() and 0xff)
}
