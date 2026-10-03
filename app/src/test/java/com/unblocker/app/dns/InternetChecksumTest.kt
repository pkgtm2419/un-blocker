package com.unblocker.app.dns

import com.unblocker.app.logic.dns.InternetChecksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InternetChecksumTest {
    private val source = ByteArray(16) { it.toByte() }
    private val destination = ByteArray(16) { (it + 16).toByte() }

    @Test fun calculatesKnownEvenLengthIpv6UdpChecksum() {
        val udp = byteArrayOf(0x00, 0x35, 0x12, 0x34, 0x00, 0x08, 0x00, 0x00)

        val checksum = InternetChecksum.udpIpv6(source, destination, udp, 0, udp.size)

        assertEquals(0xfc74, checksum.toInt() and 0xffff)
    }

    @Test fun padsKnownOddLengthIpv6UdpChecksum() {
        val udp = byteArrayOf(0x00, 0x35, 0x12, 0x34, 0x00, 0x09, 0x00, 0x00, 0xab.toByte())

        val checksum = InternetChecksum.udpIpv6(source, destination, udp, 0, udp.size)

        assertEquals(0x5172, checksum.toInt() and 0xffff)
    }

    @Test fun serializesComputedZeroAsAllOnes() {
        val zeroAddress = ByteArray(16)
        val udp = byteArrayOf(0xff.toByte(), 0xde.toByte(), 0, 0, 0, 8, 0, 0)

        val checksum = InternetChecksum.udpIpv6(zeroAddress, zeroAddress, udp, 0, udp.size)

        assertEquals(0xffff, checksum.toInt() and 0xffff)
    }

    @Test fun rejectsInvalidAddressAndSegmentBounds() {
        val udp = ByteArray(8)

        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.udpIpv6(ByteArray(4), destination, udp, 0, udp.size)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.udpIpv6(source, destination, udp, 4, 8)
        }
    }
}
