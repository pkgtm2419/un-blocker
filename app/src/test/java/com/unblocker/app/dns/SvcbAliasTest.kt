package com.unblocker.app.dns

import com.unblocker.app.logic.dns.*
import org.junit.Assert.*
import org.junit.Test

class SvcbAliasTest {
    @Test fun aliasModeIsRootedAndRequiresMatchingQuestionTypeAndClass() {
        val q=DnsFixtures.query(type=65)
        val body=DnsFixtures.response(q,listOf(
            DnsFixtures.Record("unrelated.test",65,binding(0,"evil.test")),
            DnsFixtures.Record("hop.test",65,binding(0,"tracker.test")),
            DnsFixtures.Record(q.domain,5,DnsFixtures.name("hop.test"))))
        assertEquals(listOf("hop.test","tracker.test"),DnsResponseValidator.parseAndValidate(body,body.size,q)!!.aliases)
        for(record in listOf(DnsFixtures.Record(q.domain,64,binding(0,"tracker.test")),
            DnsFixtures.Record(q.domain,65,binding(1,"tracker.test")),
            DnsFixtures.Record(q.domain,65,binding(0,"tracker.test"),clazz=3))) {
            val bytes=DnsFixtures.response(q,listOf(record))
            assertTrue(DnsResponseValidator.parseAndValidate(bytes,bytes.size,q)!!.aliases.isEmpty())
        }
    }
    @Test fun compressedTargetsAndMalformedOrDuplicateParametersAreRejected() {
        val q=DnsFixtures.query(type=65)
        for(data in listOf(byteArrayOf(0,0,0xc0.toByte(),0x0c),binding(1,"target.test",byteArrayOf(0,1,0,8,0)),
            binding(1,"target.test",byteArrayOf(0,1,0,0,0,1,0,0)))) {
            val bytes=DnsFixtures.response(q,listOf(DnsFixtures.Record(q.domain,65,data)))
            assertNull(DnsResponseValidator.parseAndValidate(bytes,bytes.size,q))
        }
    }
    private fun binding(priority:Int,target:String,params:ByteArray=byteArrayOf())=DnsFixtures.output {
        writeShort(priority);write(DnsFixtures.name(target));write(params)
    }
}
