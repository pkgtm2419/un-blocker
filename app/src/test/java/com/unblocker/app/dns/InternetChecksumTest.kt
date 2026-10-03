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

    @Test fun calculatesKnownTcpIpv4Checksum() {
        val src = byteArrayOf(10, 10, 0, 2)
        val dst = byteArrayOf(10, 10, 0, 1)
        val tcp = ByteArray(20) { 0 }
        // src port 12345 (0x3039), dst port 53 (0x0035), seq 1, ack 0, header len 20 (5 << 4 = 0x50), SYN (0x02), win 65535 (0xffff)
        tcp[0] = 0x30; tcp[1] = 0x39
        tcp[2] = 0x00; tcp[3] = 0x35
        tcp[7] = 0x01
        tcp[12] = 0x50
        tcp[13] = 0x02
        tcp[14] = 0xff.toByte(); tcp[15] = 0xff.toByte()

        val checksum = InternetChecksum.tcpIpv4(src, dst, tcp, 0, tcp.size)
        // Verify checksum verifies to 0 when placed into segment
        tcp[16] = ((checksum.toInt() ushr 8) and 0xff).toByte()
        tcp[17] = (checksum.toInt() and 0xff).toByte()
        val verify = InternetChecksum.tcpIpv4(src, dst, tcp, 0, tcp.size)
        assertEquals(0xffff, verify.toInt() and 0xffff)
    }

    @Test fun calculatesKnownTcpIpv6Checksum() {
        val src = ByteArray(16) { 0x01 }
        val dst = ByteArray(16) { 0x02 }
        val tcp = ByteArray(20) { 0 }
        tcp[0] = 0x30; tcp[1] = 0x39
        tcp[2] = 0x00; tcp[3] = 0x35
        tcp[7] = 0x01
        tcp[12] = 0x50
        tcp[13] = 0x02
        tcp[14] = 0xff.toByte(); tcp[15] = 0xff.toByte()

        val checksum = InternetChecksum.tcpIpv6(src, dst, tcp, 0, tcp.size)
        tcp[16] = ((checksum.toInt() ushr 8) and 0xff).toByte()
        tcp[17] = (checksum.toInt() and 0xff).toByte()
        val verify = InternetChecksum.tcpIpv6(src, dst, tcp, 0, tcp.size)
        assertEquals(0xffff, verify.toInt() and 0xffff)
    }

    @Test fun rejectsInvalidAddressAndSegmentBounds() {
        val udp = ByteArray(8)

        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.udpIpv6(ByteArray(4), destination, udp, 0, udp.size)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.udpIpv6(source, destination, udp, 4, 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.tcpIpv4(ByteArray(16), ByteArray(16), ByteArray(20), 0, 20)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InternetChecksum.tcpIpv6(ByteArray(4), ByteArray(4), ByteArray(20), 0, 20)
        }
    }
}
