package com.unblocker.app.dns

import com.unblocker.app.logic.dns.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class PacketPropertiesTest {
    @Test fun addressRecordsHaveExactWireLengths() {
        for(type in listOf(1,28)) {
            val query=DnsFixtures.query(type=type)
            val expected=if(type==1)4 else 16
            for(size in listOf(0,1,expected-1,expected+1)) {
                val reply=DnsFixtures.response(query,listOf(DnsFixtures.Record(query.domain,type,ByteArray(size))))
                assertNull("Invalid address RDATA length $type/$size",DnsResponseValidator.parseAndValidate(reply,reply.size,query))
            }
            val reply=DnsFixtures.response(query,listOf(DnsFixtures.Record(query.domain,type,ByteArray(expected))))
            assertNotNull(DnsResponseValidator.parseAndValidate(reply,reply.size,query))
        }
    }
    @Test fun nxdomainAndNodataAcrossFamiliesAndMalformedFuzzNeverCrash() {
        for(ipv6 in listOf(false,true)) for(type in listOf(1,28,65)) for(rcode in listOf(0,3)) {
            val wire=DnsFixtures.packet(DnsFixtures.question(type=type),ipv6)
            val query=DnsPacketUtil.parseIpPacket(wire,wire.size)!!
            val reply=DnsFixtures.response(query,rcode=rcode)
            assertEquals(rcode,DnsResponseValidator.parseAndValidate(reply,reply.size,query)!!.rcode)
        }
        val random=Random(55801)
        val query=DnsFixtures.query()
        repeat(20000) {
            val bytes=ByteArray(random.nextInt(512)); random.nextBytes(bytes)
            val length=random.nextInt(bytes.size+3)-1
            DnsPacketUtil.parseIpPacket(bytes,length)
            DnsResponseValidator.parseAndValidate(bytes,length,query)
        }
    }
}
