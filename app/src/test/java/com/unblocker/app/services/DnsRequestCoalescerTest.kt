package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DnsRequestCoalescerTest {

    private lateinit var coalescer: DnsRequestCoalescer

    @Before
    fun setup() {
        coalescer = DnsRequestCoalescer(maxWaitersPerKey = 4)
    }

    private fun makeQuery(domain: String, txId: Short, type: Short = DnsPacketUtil.TYPE_A): DnsQuery {
        return DnsQuery(
            transactionId = txId,
            domain = domain,
            queryType = type,
            queryClass = DnsPacketUtil.CLASS_IN,
            rawPacket = ByteArray(0),
            dnsOffset = 0,
            dnsLength = 0,
            srcIp = byteArrayOf(10, 10, 0, 2),
            dstIp = byteArrayOf(10, 10, 0, 1),
            srcPort = 10000 + txId.toInt(),
            dstPort = 53
        )
    }

    @Test
    fun testFirstQueryBecomesLeader() {
        val query = makeQuery("example.com", 1)
        val key = CoalesceKey("example.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)

        val result = coalescer.attachOrLead(key, query)
        assertTrue("First query must be designated as leader", result is DnsRequestCoalescer.JoinResult.Leader)
        assertEquals(1, coalescer.activeInFlightCount)
    }

    @Test
    fun testConcurrentIdenticalQueriesAttachToLeader() {
        val key = CoalesceKey("example.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)
        val q1 = makeQuery("example.com", 1)
        val q2 = makeQuery("example.com", 2)
        val q3 = makeQuery("example.com", 3)

        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(key, q1))
        assertEquals(DnsRequestCoalescer.JoinResult.Joined, coalescer.attachOrLead(key, q2))
        assertEquals(DnsRequestCoalescer.JoinResult.Joined, coalescer.attachOrLead(key, q3))

        assertEquals("Only one in-flight leader key should exist", 1, coalescer.activeInFlightCount)

        val all = coalescer.complete(key)
        assertEquals(3, all.size)
        assertEquals(1.toShort(), all[0].transactionId)
        assertEquals(2.toShort(), all[1].transactionId)
        assertEquals(3.toShort(), all[2].transactionId)

        assertEquals("After completion in-flight count must be 0", 0, coalescer.activeInFlightCount)
    }

    @Test
    fun testDifferentKeysRemainIndependentLeaders() {
        val keyA = CoalesceKey("example.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)
        val keyAaaa = CoalesceKey("example.com", DnsPacketUtil.TYPE_AAAA, DnsPacketUtil.CLASS_IN, false, 1L)
        val keyOther = CoalesceKey("google.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)

        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(keyA, makeQuery("example.com", 1, DnsPacketUtil.TYPE_A)))
        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(keyAaaa, makeQuery("example.com", 2, DnsPacketUtil.TYPE_AAAA)))
        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(keyOther, makeQuery("google.com", 3, DnsPacketUtil.TYPE_A)))

        assertEquals(3, coalescer.activeInFlightCount)
    }

    @Test
    fun testWaitersCappedAtMaxCapacity() {
        val key = CoalesceKey("capped.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)
        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(key, makeQuery("capped.com", 0)))

        for (i in 1..4) {
            assertEquals(DnsRequestCoalescer.JoinResult.Joined, coalescer.attachOrLead(key, makeQuery("capped.com", i.toShort())))
        }

        val overflow = coalescer.attachOrLead(key, makeQuery("capped.com", 5))
        assertEquals("Exceeding max capacity must return Overflow", DnsRequestCoalescer.JoinResult.Overflow, overflow)

        val all = coalescer.complete(key)
        assertEquals(5, all.size) // 1 leader + 4 waiters
    }

    @Test
    fun testSubsequentQueryAfterCompletionBecomesNewLeader() {
        val key = CoalesceKey("sequential.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)
        val q1 = makeQuery("sequential.com", 1)
        val q2 = makeQuery("sequential.com", 2)

        assertEquals(DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(key, q1))
        val completed = coalescer.complete(key)
        assertEquals(1, completed.size)

        assertEquals("Subsequent query must become leader again", DnsRequestCoalescer.JoinResult.Leader, coalescer.attachOrLead(key, q2))
    }

    @Test
    fun testClearDrainsAllQueriesAndEmptiesState() {
        val key1 = CoalesceKey("a.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)
        val key2 = CoalesceKey("b.com", DnsPacketUtil.TYPE_A, DnsPacketUtil.CLASS_IN, false, 1L)

        coalescer.attachOrLead(key1, makeQuery("a.com", 1))
        coalescer.attachOrLead(key1, makeQuery("a.com", 2))
        coalescer.attachOrLead(key2, makeQuery("b.com", 3))

        val cleared = coalescer.clear()
        assertEquals(3, cleared.size)
        assertEquals(0, coalescer.activeInFlightCount)
    }
}
