package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import com.unblocker.app.logic.dns.DnsResponseValidator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResponseValidatorTest {
    private val query = DnsQuery(
        transactionId = 0x1234,
        domain = "ads.example",
        queryType = DnsPacketUtil.TYPE_A,
        queryClass = DnsPacketUtil.CLASS_IN,
        rawPacket = ByteArray(0),
        dnsOffset = 0,
        dnsLength = 0,
        srcIp = byteArrayOf(10, 10, 0, 2),
        dstIp = byteArrayOf(10, 10, 0, 1),
        srcPort = 45_678,
        dstPort = 53
    )

    @Test fun acceptsMatchingResponseAndExtractsMetadata() {
        val response = response(answerTtl = 120)

        val metadata = DnsResponseValidator.parseAndValidate(response, response.size, query)!!

        assertEquals(0x1234, metadata.transactionId)
        assertEquals("ads.example", metadata.questionName)
        assertEquals(1, metadata.questionType)
        assertEquals(1, metadata.questionClass)
        assertFalse(metadata.truncated)
        assertEquals(0, metadata.rcode)
        assertEquals(120L, metadata.minPositiveTtlSeconds)
        assertEquals(emptyList<String>(), metadata.aliases)
    }

    @Test fun collectsCanonicalCnameAlias() {
        val response = response(cnameAlias = "Tracker.Example", answerTtl = 90)

        val metadata = DnsResponseValidator.parseAndValidate(response, response.size, query)!!

        assertEquals(listOf("tracker.example"), metadata.aliases)
        assertEquals(90L, metadata.minPositiveTtlSeconds)
    }

    @Test fun acceptsTruncatedResponseQuestionWithoutParsingMissingAnswer() {
        val response = response(flags = 0x8380, includeAnswerBytes = false)

        val metadata = DnsResponseValidator.parseAndValidate(response, response.size, query)!!

        assertTrue(metadata.truncated)
        assertNull(metadata.minPositiveTtlSeconds)
        assertEquals(emptyList<String>(), metadata.aliases)
    }

    @Test fun rejectsPacketWhoseQrFlagStillMarksAQuery() {
        val response = response(flags = 0x0100)
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsWrongTransactionId() {
        val response = response(transactionId = 0x4321)
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsQuestionCountOtherThanOne() {
        val response = response(questionCount = 0)
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsSameTransactionIdWithWrongQuestionName() {
        val response = response(questionName = "other.example")
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsWrongQuestionType() {
        val response = response(questionType = DnsPacketUtil.TYPE_AAAA.toInt())
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsWrongQuestionClass() {
        val response = response(questionClass = 3)
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsTruncatedQuestionWithoutThrowing() {
        val response = response().copyOf(15)
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsOutOfRangeCompressionPointer() {
        val response = responseWithQuestionBytes(byteArrayOf(0xc0.toByte(), 0xff.toByte()))
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsCyclicCompressionPointer() {
        val response = responseWithQuestionBytes(byteArrayOf(0xc0.toByte(), 0x0c))
        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsQuestionWhoseSingleWireLabelContainsADot() {
        val response = responseWithQuestionBytes(encodeLabels("ads.example"))

        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun rejectsQuestionWhoseWireLabelWouldOnlyMatchAfterTrimming() {
        val response = responseWithQuestionBytes(encodeLabels(" ads", "example"))

        assertNull(DnsResponseValidator.parseAndValidate(response, response.size, query))
    }

    @Test fun zeroTtlInAnyAnswerMakesWholeResponseNonCacheable() {
        val response = responseWithCnameAndAddressTtls(cnameTtl = 0, addressTtl = 120)

        val metadata = DnsResponseValidator.parseAndValidate(response, response.size, query)!!

        assertEquals(listOf("target.example"), metadata.aliases)
        assertNull(metadata.minPositiveTtlSeconds)
    }

    @Test fun rootedAliasesIgnoreUnrelatedAnswersAndRespectChainOrder() {
        val bytes = chain("other.test" to "tracker.test", "hop.test" to "end.test",
            "ads.example" to "hop.test")
        assertEquals(listOf("hop.test", "end.test"), DnsResponseValidator.parseAndValidate(bytes, bytes.size, query)!!.aliases)
    }

    @Test fun cyclicConflictingAndOverlongChainsAreRejected() {
        for (edges in listOf(listOf("ads.example" to "hop.test", "hop.test" to "ads.example"),
            listOf("ads.example" to "one.test", "ads.example" to "two.test"),
            (0..8).map { (if (it == 0) "ads.example" else "h$it.test") to "h${it+1}.test" })) {
            val bytes = chain(*edges.toTypedArray())
            assertNull(DnsResponseValidator.parseAndValidate(bytes, bytes.size, query))
        }
    }

    private fun chain(vararg edges: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { d ->
            d.writeShort(0x1234); d.writeShort(0x8180); d.writeShort(1)
            d.writeShort(edges.size); d.writeShort(0); d.writeShort(0)
            d.write(encodeName("ads.example")); d.writeShort(1); d.writeShort(1)
            edges.forEach { (owner, target) ->
                d.write(encodeName(owner)); d.writeShort(5); d.writeShort(1); d.writeInt(60)
                val name = encodeName(target); d.writeShort(name.size); d.write(name)
            }
        }
        return output.toByteArray()
    }

    private fun response(
        transactionId: Int = 0x1234,
        flags: Int = 0x8180,
        questionCount: Int = 1,
        questionName: String = "ads.example",
        questionType: Int = 1,
        questionClass: Int = 1,
        answerTtl: Int = 120,
        cnameAlias: String? = null,
        includeAnswerBytes: Boolean = true
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(transactionId)
            data.writeShort(flags)
            data.writeShort(questionCount)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.write(encodeName(questionName))
            data.writeShort(questionType)
            data.writeShort(questionClass)
            if (includeAnswerBytes) {
                data.writeShort(0xc00c)
                if (cnameAlias == null) {
                    data.writeShort(1)
                    data.writeShort(1)
                    data.writeInt(answerTtl)
                    data.writeShort(4)
                    data.write(byteArrayOf(192.toByte(), 0, 2, 1))
                } else {
                    val aliasBytes = encodeName(cnameAlias)
                    data.writeShort(5)
                    data.writeShort(1)
                    data.writeInt(answerTtl)
                    data.writeShort(aliasBytes.size)
                    data.write(aliasBytes)
                }
            }
        }
        return output.toByteArray()
    }

    private fun responseWithQuestionBytes(questionBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(0x1234)
            data.writeShort(0x8180)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.writeShort(0)
            data.write(questionBytes)
            data.writeShort(1)
            data.writeShort(1)
        }
        return output.toByteArray()
    }

    private fun responseWithCnameAndAddressTtls(cnameTtl: Int, addressTtl: Int): ByteArray {
        val alias = encodeName("target.example")
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(0x1234)
            data.writeShort(0x8180)
            data.writeShort(1)
            data.writeShort(2)
            data.writeShort(0)
            data.writeShort(0)
            data.write(encodeName("ads.example"))
            data.writeShort(1)
            data.writeShort(1)
            data.writeShort(0xc00c)
            data.writeShort(5)
            data.writeShort(1)
            data.writeInt(cnameTtl)
            data.writeShort(alias.size)
            data.write(alias)
            data.writeShort(0xc00c)
            data.writeShort(1)
            data.writeShort(1)
            data.writeInt(addressTtl)
            data.writeShort(4)
            data.write(byteArrayOf(192.toByte(), 0, 2, 1))
        }
        return output.toByteArray()
    }

    private fun encodeLabels(vararg labels: String): ByteArray {
        val output = ByteArrayOutputStream()
        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        return output.toByteArray()
    }

    private fun encodeName(domain: String): ByteArray {
        val output = ByteArrayOutputStream()
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        return output.toByteArray()
    }
}
