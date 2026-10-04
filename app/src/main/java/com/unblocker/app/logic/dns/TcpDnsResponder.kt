package com.unblocker.app.logic.dns

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Minimal user-space TCP DNS responder serving on 10.10.0.1:53 and [fd00:1::1]:53.
 *
 * Enforces hard bounds:
 * - <= 32 concurrent connections
 * - <= 64 KB buffer per connection
 * - 5 s idle timeout
 * - Drops out-of-order segments
 * - Sends RST on unexpected traffic or bounds violation
 */
class TcpDnsResponder(
    private val executor: java.util.concurrent.Executor,
    private val emit: (List<ByteArray>) -> Unit,
    private val queryResolver: (dnsWireBytes: ByteArray, isIpv6: Boolean, clientIp: ByteArray) -> ByteArray?,
    private val maxConnections: Int = 32,
    private val maxBufferSize: Int = 65536,
    private val idleTimeoutMillis: Long = 5000L
) {
    private val connections = HashMap<TcpConnectionKey, TcpConnection>()
    private val lock = Any()

    companion object {
        const val FLAG_FIN = 0x01
        const val FLAG_SYN = 0x02
        const val FLAG_RST = 0x04
        const val FLAG_PSH = 0x08
        const val FLAG_ACK = 0x10

        private val IPV4_DNS = byteArrayOf(10, 10, 0, 1)
        private val IPV6_DNS = byteArrayOf(
            0xfd.toByte(), 0x00, 0x00, 0x01,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 1
        )
    }

    class TcpConnectionKey(val ip: ByteArray, val port: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TcpConnectionKey
            if (port != other.port) return false
            if (!ip.contentEquals(other.ip)) return false
            return true
        }
        override fun hashCode(): Int {
            return 31 * ip.contentHashCode() + port
        }
    }

    private class TcpConnection(
        val key: TcpConnectionKey,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        var state: State,
        var clientSeq: Long,
        var serverSeq: Long,
        var lastActivityTime: Long,
        val buffer: ByteArrayOutputStream = ByteArrayOutputStream(),
        var queryAnswered: Boolean = false
    ) {
        enum class State { SYN_RECEIVED, ESTABLISHED, CLOSED }
    }

    /**
     * Processes an incoming raw IP packet.
     * Returns a list of raw IP response packets to be written back to the TUN interface.
     */
    fun processPacket(packet: ByteArray, length: Int) {
        if (length < 20) return

        val isIpv6 = (packet[0].toInt() and 0xF0) == 0x60
        if (!isIpv6) {
            val totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
            if (length < totalLen) return
            val fragOffset = ((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF)
            if (fragOffset != 0) return
        }
        val (tcpOffset, srcIp, dstIp) = if (isIpv6) {
            if (length < 60) return // 40 IP + 20 TCP
            var offset = 40
            var nextHeader = packet[6].toInt() and 255
            while (nextHeader in setOf(0, 43, 60, 44)) {
                if (offset + 2 > length) return
                val extLen = when (nextHeader) {
                    44 -> 8
                    else -> ((packet[offset + 1].toInt() and 255) + 1) * 8
                }
                if (offset + extLen + 20 > length) return
                nextHeader = packet[offset].toInt() and 255
                offset += extLen
            }
            if (nextHeader != 6) return // Protocol 6 = TCP
            val src = ByteArray(16) { packet[8 + it] }
            val dst = ByteArray(16) { packet[24 + it] }
            Triple(offset, src, dst)
        } else {
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (ihl < 20 || length < ihl + 20) return
            if (packet[9].toInt() and 0xFF != 6) return // Protocol 6 = TCP
            val src = ByteArray(4) { packet[12 + it] }
            val dst = ByteArray(4) { packet[16 + it] }
            Triple(ihl, src, dst)
        }

        val dstPort = ((packet[tcpOffset + 2].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        // Validate local destination address
        if (isIpv6) {
            if (!dstIp.contentEquals(IPV6_DNS)) return
        } else {
            if (!dstIp.contentEquals(IPV4_DNS)) return
        }

        val srcPort = ((packet[tcpOffset].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 1].toInt() and 0xFF)
        val seqNum = ((packet[tcpOffset + 4].toLong() and 0xFF) shl 24) or
            ((packet[tcpOffset + 5].toLong() and 0xFF) shl 16) or
            ((packet[tcpOffset + 6].toLong() and 0xFF) shl 8) or
            (packet[tcpOffset + 7].toLong() and 0xFF)
        val ackNum = ((packet[tcpOffset + 8].toLong() and 0xFF) shl 24) or
            ((packet[tcpOffset + 9].toLong() and 0xFF) shl 16) or
            ((packet[tcpOffset + 10].toLong() and 0xFF) shl 8) or
            (packet[tcpOffset + 11].toLong() and 0xFF)

        val dataOffset = (packet[tcpOffset + 12].toInt() and 0xF0) shr 4
        val tcpHeaderLen = dataOffset * 4
        if (tcpHeaderLen < 20 || tcpOffset + tcpHeaderLen > length) return

        val flags = packet[tcpOffset + 13].toInt() and 0xFF
        val payloadLen = length - (tcpOffset + tcpHeaderLen)
        val payloadOffset = tcpOffset + tcpHeaderLen

        val now = System.currentTimeMillis()
        val key = TcpConnectionKey(srcIp, srcPort)

        synchronized(lock) {
            // Purge idle connections
            val it = connections.values.iterator()
            while (it.hasNext()) {
                val conn = it.next()
                if (now - conn.lastActivityTime > idleTimeoutMillis) {
                    it.remove()
                }
            }

            // Handle RST flag
            if ((flags and FLAG_RST) != 0) {
                connections.remove(key)
                return
            }

            // Handle SYN (handshake initiation)
            if ((flags and FLAG_SYN) != 0 && (flags and FLAG_ACK) == 0) {
                if (connections.containsKey(key)) {
                    val existing = connections[key]!!
                    if (existing.state == TcpConnection.State.SYN_RECEIVED && existing.clientSeq == ((seqNum + 1) and 0xFFFFFFFFL)) {
                        val synAck = buildTcpPacket(
                            isIpv6 = isIpv6,
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = 53,
                            dstPort = srcPort,
                            seqNum = (existing.serverSeq - 1) and 0xFFFFFFFFL,
                            ackNum = existing.clientSeq,
                            flags = FLAG_SYN or FLAG_ACK,
                            payload = null
                        )
                        emit(listOf(synAck))
                        return
                    }
                }
                if (connections.size >= maxConnections && !connections.containsKey(key)) {
                    // SYN flood or max connections exceeded: send RST
                    emit(listOf(buildTcpPacket(isIpv6, dstIp, srcIp, 53, srcPort, 0, (seqNum + 1) and 0xFFFFFFFFL, FLAG_RST or FLAG_ACK, null))); return
                }
                val serverIsn = Random.nextLong(0, 0xFFFFFFFFL)
                val conn = TcpConnection(
                    key = key,
                    srcIp = srcIp,
                    dstIp = dstIp,
                    state = TcpConnection.State.SYN_RECEIVED,
                    clientSeq = (seqNum + 1) and 0xFFFFFFFFL,
                    serverSeq = (serverIsn + 1) and 0xFFFFFFFFL,
                    lastActivityTime = now
                )
                connections[key] = conn
                val synAck = buildTcpPacket(
                    isIpv6 = isIpv6,
                    srcIp = dstIp,
                    dstIp = srcIp,
                    srcPort = 53,
                    dstPort = srcPort,
                    seqNum = serverIsn,
                    ackNum = conn.clientSeq,
                    flags = FLAG_SYN or FLAG_ACK,
                    payload = null
                )
                emit(listOf(synAck)); return
            }

            val conn = connections[key]
            if (conn == null) {
                // Non-SYN packet on unknown connection: send RST
                val rstAck = if ((flags and FLAG_ACK) == 0) {
                    buildTcpPacket(isIpv6, dstIp, srcIp, 53, srcPort, 0, (seqNum + payloadLen.coerceAtLeast(1)) and 0xFFFFFFFFL, FLAG_RST or FLAG_ACK, null)
                } else {
                    buildTcpPacket(isIpv6, dstIp, srcIp, 53, srcPort, ackNum, 0, FLAG_RST, null)
                }
                emit(listOf(rstAck))
                return
            }

            conn.lastActivityTime = now

            // Drop out-of-order segments
            if (seqNum != conn.clientSeq) {
                return
            }

            // Handle FIN
            if ((flags and FLAG_FIN) != 0) {
                conn.clientSeq = (conn.clientSeq + 1) and 0xFFFFFFFFL
                val finAck = buildTcpPacket(
                    isIpv6 = isIpv6,
                    srcIp = dstIp,
                    dstIp = srcIp,
                    srcPort = 53,
                    dstPort = srcPort,
                    seqNum = conn.serverSeq,
                    ackNum = conn.clientSeq,
                    flags = FLAG_FIN or FLAG_ACK,
                    payload = null
                )
                connections.remove(key)
                emit(listOf(finAck)); return
            }

            // Complete handshake if in SYN_RECEIVED
            if (conn.state == TcpConnection.State.SYN_RECEIVED) {
                if ((flags and FLAG_ACK) != 0) {
                    conn.state = TcpConnection.State.ESTABLISHED
                }
            }

            val responses = mutableListOf<ByteArray>()

            // Process payload
            if (payloadLen > 0) {
                if (conn.buffer.size() + payloadLen > maxBufferSize) {
                    // Buffer bound exceeded: send RST and close
                    connections.remove(key)
                    emit(listOf(buildTcpPacket(isIpv6, dstIp, srcIp, 53, srcPort, conn.serverSeq, conn.clientSeq, FLAG_RST or FLAG_ACK, null))); return
                }

                conn.buffer.write(packet, payloadOffset, payloadLen)
                conn.clientSeq = (conn.clientSeq + payloadLen) and 0xFFFFFFFFL

                val bufBytes = conn.buffer.toByteArray()
                if (!conn.queryAnswered && bufBytes.size >= 2) {
                    val queryLen = ((bufBytes[0].toInt() and 0xFF) shl 8) or (bufBytes[1].toInt() and 0xFF)
                    if (bufBytes.size >= 2 + queryLen) {
                        val dnsQueryBytes = bufBytes.copyOfRange(2, 2 + queryLen)
                        conn.queryAnswered = true
                        
                        // Emit bare ACK immediately for the buffered query payload
                        val bareAck = buildTcpPacket(
                            isIpv6 = isIpv6,
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = 53,
                            dstPort = srcPort,
                            seqNum = conn.serverSeq,
                            ackNum = conn.clientSeq,
                            flags = FLAG_ACK,
                            payload = null
                        )
                        responses.add(bareAck)
                        emit(responses.toList())
                        responses.clear()
                        
                        val savedSeq = conn.serverSeq
                        
                        try {
                            executor.execute {
                                val dnsResponse = try {
                                    queryResolver(dnsQueryBytes, isIpv6, srcIp)
                                } catch (e: Exception) {
                                    null
                                }
                                
                                synchronized(lock) {
                                    val currentConn = connections[key]
                                    if (currentConn != null && currentConn.serverSeq == savedSeq && currentConn.state != TcpConnection.State.CLOSED) {
                                        if (dnsResponse != null) {
                                            val respLen = dnsResponse.size
                                            val respPayload = ByteArray(2 + respLen)
                                            respPayload[0] = ((respLen shr 8) and 0xFF).toByte()
                                            respPayload[1] = (respLen and 0xFF).toByte()
                                            System.arraycopy(dnsResponse, 0, respPayload, 2, respLen)

                                            // Segment the response by MSS
                                            val mss = 1240
                                            var offset = 0
                                            val outPackets = mutableListOf<ByteArray>()
                                            while (offset < respPayload.size) {
                                                val chunkLen = minOf(mss, respPayload.size - offset)
                                                val chunk = respPayload.copyOfRange(offset, offset + chunkLen)
                                                val isLast = offset + chunkLen == respPayload.size
                                                val flagsOut = if (isLast) FLAG_PSH or FLAG_ACK else FLAG_ACK
                                                val p = buildTcpPacket(
                                                    isIpv6 = isIpv6,
                                                    srcIp = dstIp,
                                                    dstIp = srcIp,
                                                    srcPort = 53,
                                                    dstPort = srcPort,
                                                    seqNum = currentConn.serverSeq,
                                                    ackNum = currentConn.clientSeq,
                                                    flags = flagsOut,
                                                    payload = chunk
                                                )
                                                currentConn.serverSeq = (currentConn.serverSeq + chunkLen) and 0xFFFFFFFFL
                                                outPackets.add(p)
                                                offset += chunkLen
                                            }
                                            emit(outPackets)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Pool rejected task, ignore
                        }
                    }
                }
            }

            if (responses.isNotEmpty()) emit(responses)
        }
    }

    fun activeConnectionCount(): Int = synchronized(lock) { connections.size }

    fun clear() = synchronized(lock) { connections.clear() }

    private fun buildTcpPacket(
        isIpv6: Boolean,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray?
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val tcpSegmentLen = 20 + payloadLen
        val ipHeaderLen = if (isIpv6) 40 else 20
        val totalLen = ipHeaderLen + tcpSegmentLen

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        if (isIpv6) {
            buf.putInt(0x60000000)
            buf.putShort(tcpSegmentLen.toShort())
            buf.put(6.toByte()) // Protocol 6 = TCP
            buf.put(64.toByte()) // Hop limit
            buf.put(srcIp)
            buf.put(dstIp)
        } else {
            buf.put(0x45.toByte()) // IPv4, IHL 5
            buf.put(0.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0x4000.toShort()) // DF
            buf.put(64.toByte()) // TTL
            buf.put(6.toByte()) // Protocol 6 = TCP
            buf.putShort(0.toShort()) // Checksum placeholder
            buf.put(srcIp)
            buf.put(dstIp)
            buf.putShort(10, InternetChecksum.ipv4Header(buf.array(), 0, 20))
        }

        val tcpOffset = ipHeaderLen
        buf.position(tcpOffset)
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putInt(seqNum.toInt())
        buf.putInt(ackNum.toInt())
        buf.put(0x50.toByte()) // Data offset 5 (20 bytes)
        buf.put(flags.toByte())
        buf.putShort(0xffff.toShort()) // Window size 65535
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.putShort(0.toShort()) // Urgent pointer

        if (payload != null) {
            buf.put(payload)
        }

        val checksum = if (isIpv6) {
            InternetChecksum.tcpIpv6(srcIp, dstIp, buf.array(), tcpOffset, tcpSegmentLen)
        } else {
            InternetChecksum.tcpIpv4(srcIp, dstIp, buf.array(), tcpOffset, tcpSegmentLen)
        }
        buf.putShort(tcpOffset + 16, checksum)

        return buf.array()
    }
}
