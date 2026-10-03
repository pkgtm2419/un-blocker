package com.unblocker.app.dns

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import com.unblocker.app.logic.dns.*

internal object DnsFixtures {
    fun name(domain:String):ByteArray = ByteArrayOutputStream().apply {
        if(domain.isNotEmpty()) domain.split('.').forEach { write(it.length); write(it.toByteArray()) }
        write(0)
    }.toByteArray()
    fun question(domain:String="shop.test",type:Int=1,additional:Int=0):ByteArray = output {
        writeShort(0x1234);writeShort(0x100);writeShort(1);writeShort(0);writeShort(0);writeShort(additional)
        write(name(domain));writeShort(type);writeShort(1)
    }
    fun packet(dns:ByteArray,ipv6:Boolean=false):ByteArray {
        val ip=if(ipv6)40 else 20
        val b=ByteArray(ip+8+dns.size)
        b[0]=if(ipv6)0x60 else 0x45
        if(ipv6) { u16(b,4,b.size-40);b[6]=17;b[7]=64;b[23]=2;b[39]=1 }
        else {u16(b,2,b.size);b[8]=64;b[9]=17;b[12]=10;b[15]=2;b[16]=10;b[19]=1}
        u16(b,ip,45000);u16(b,ip+2,53);u16(b,ip+4,dns.size+8)
        dns.copyInto(b,ip+8)
        return b
    }
    fun query(domain:String="shop.test",type:Int=1):DnsQuery {
        val b=packet(question(domain,type))
        return DnsPacketUtil.parseIpPacket(b,b.size)!!
    }
    data class Record(val owner:String,val type:Int,val data:ByteArray,val clazz:Int=1,val ttl:Int=60)
    fun response(query:DnsQuery=query(),records:List<Record> = emptyList(),rcode:Int=0):ByteArray = output {
        writeShort(query.transactionId.toInt());writeShort(0x8180 or rcode);writeShort(1)
        writeShort(records.size);writeShort(0);writeShort(0)
        write(name(query.domain));writeShort(query.queryType.toInt());writeShort(query.queryClass.toInt())
        records.forEach { r -> write(name(r.owner));writeShort(r.type);writeShort(r.clazz)
            writeInt(r.ttl);writeShort(r.data.size);write(r.data) }
    }
    fun output(action:DataOutputStream.()->Unit):ByteArray = ByteArrayOutputStream().let { b ->
        DataOutputStream(b).use { it.action() }; b.toByteArray()
    }
    fun u16(bytes:ByteArray,offset:Int,value:Int) {bytes[offset]=(value ushr 8).toByte();bytes[offset+1]=value.toByte()}
}
