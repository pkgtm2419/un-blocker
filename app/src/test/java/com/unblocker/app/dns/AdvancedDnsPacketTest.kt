package com.unblocker.app.dns

import com.unblocker.app.logic.dns.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedDnsPacketTest {
    @Test fun boundedIpv6ExtensionHeadersAndAtomicFragmentAreSupported() {
        val base=DnsFixtures.packet(DnsFixtures.question(),true)
        for(type in listOf(0,43,60,44)) {
            val b=base.copyOfRange(0,40)+byteArrayOf(17,0,0,0,0,0,0,0)+base.copyOfRange(40,base.size)
            b[6]=type.toByte();DnsFixtures.u16(b,4,b.size-40)
            assertNotNull("Header $type",DnsPacketUtil.parseIpPacket(b,b.size))
            if(type==44) { b[43]=1;assertNull(DnsPacketUtil.parseIpPacket(b,b.size)) }
        }
    }
    @Test fun invalidPacketLengthsAndIpv4FragmentsAreRejected() {
        val b=DnsFixtures.packet(DnsFixtures.question())
        assertNull(DnsPacketUtil.parseIpPacket(b,b.size+10))
        DnsFixtures.u16(b,2,29);assertNull(DnsPacketUtil.parseIpPacket(b,b.size))
        DnsFixtures.u16(b,2,b.size);b[6]=0x20;assertNull(DnsPacketUtil.parseIpPacket(b,b.size))
    }
    @Test fun ednsNegotiationValidatesOptAndSyntheticResponseIncludesOpt() {
        val dns=DnsFixtures.question(additional=1)+opt(1232)
        val b=DnsFixtures.packet(dns)
        val q=DnsPacketUtil.parseIpPacket(b,b.size)!!
        assertTrue(q.hasEdns);assertEquals(1232,q.udpPayloadLimit)
        val blocked=DnsPacketUtil.buildBlockedDnsResponsePacket(q)
        assertEquals(1,blocked[39].toInt()) // DNS ARCOUNT low byte: IP20 + UDP8 +11.
        val malformed=DnsFixtures.packet(DnsFixtures.question(additional=1)+opt(1232,byteArrayOf(0,1,0,8,0)))
        assertNull(DnsPacketUtil.parseIpPacket(malformed,malformed.size))
        val duplicate=DnsFixtures.packet(DnsFixtures.question(additional=2)+opt(1232)+opt(1232))
        assertNull(DnsPacketUtil.parseIpPacket(duplicate,duplicate.size))
    }
    @Test fun clientUdpLimitIsPerTransactionAndOversizedRepliesAreSafelyTruncated() {
        val q=DnsFixtures.query()
        val body=DnsFixtures.response(q,listOf(DnsFixtures.Record(q.domain,16,ByteArray(900))))
        val b=DnsPacketUtil.wrapClientResponse(q,body)
        assertTrue(b.size<512+28);assertTrue(b[30].toInt() and 2 != 0)
        val m=DnsResponseValidator.parseAndValidate(b.copyOfRange(28,b.size),b.size-28,q)!!
        assertTrue(m.truncated)
        val large=DnsPacketUtil.wrapClientResponse(q.copy(udpPayloadLimit=1232),body)
        assertEquals(body.size+28,large.size)
    }
    private fun opt(size:Int,options:ByteArray=byteArrayOf()):ByteArray = DnsFixtures.output {
        writeByte(0);writeShort(41);writeShort(size);writeInt(0);writeShort(options.size);write(options)
    }
}
