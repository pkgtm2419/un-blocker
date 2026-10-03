package com.unblocker.app.logic.dns

import com.unblocker.app.services.ValidatedDnsResponse
import java.util.Collections

/** Bounded volatile cache; current decisions are evaluated before delivery. */
class DnsCache(
    private val maxEntries:Int=1000,
    private val nowMillis:()->Long={System.nanoTime()/1_000_000}
) {
    private data class CacheKey(val domain:String,val type:Short,val clazz:Short,val ipv6:Boolean,val variant:String="")
    private data class Record(val bytes:ByteArray,val created:Long,val expires:Long,val metadata:DnsResponseMetadata?)
    private val cache=LinkedHashMap<CacheKey,Record>(16,.75f,true)
    init {require(maxEntries>0)}

    @Synchronized fun get(domain:String,queryType:Short,transactionId:Short,
        queryClass:Short=1,isIpv6:Boolean=false):ByteArray? =
        record(CacheKey(domain,queryType,queryClass,isIpv6))?.let { payload(it,transactionId) }

    @Synchronized internal fun getValidated(query:DnsQuery):ValidatedDnsResponse? {
        if(!query.cacheAllowed) return null
        val record=record(CacheKey(query.domain,query.queryType,query.queryClass,query.isIpv6,query.cacheVariant)) ?: return null
        val metadata=record.metadata ?: return null
        val elapsed=((nowMillis()-record.created).coerceAtLeast(0)/1000)
        return ValidatedDnsResponse(payload(record,query.transactionId),metadata.copy(
            transactionId=query.transactionId.toInt() and 0xffff,
            minPositiveTtlSeconds=metadata.minPositiveTtlSeconds?.let {(it-elapsed).coerceAtLeast(0)}))
    }
    @Synchronized fun put(domain:String,queryType:Short,dnsPayload:ByteArray,ttlSeconds:Long,
        queryClass:Short=1,isIpv6:Boolean=false) {
        insert(CacheKey(domain,queryType,queryClass,isIpv6),dnsPayload,ttlSeconds,null)
    }
    @Synchronized internal fun putValidated(query:DnsQuery,response:ValidatedDnsResponse) {
        val m=response.metadata
        if(!query.cacheAllowed || m.truncated || m.rcode!=0 || m.questionName!=query.domain ||
            m.questionType!=query.queryType.toInt() and 0xffff ||
            m.questionClass!=query.queryClass.toInt() and 0xffff ||
            response.bytes.size>DnsPacketUtil.maxDnsPayloadLength(query)) return
        val ttl=m.minPositiveTtlSeconds ?: return
        val immutable=m.copy(aliases=Collections.unmodifiableList(ArrayList(m.aliases)),
            ttlOffsets=Collections.unmodifiableList(ArrayList(m.ttlOffsets)))
        insert(CacheKey(query.domain,query.queryType,query.queryClass,query.isIpv6,query.cacheVariant),response.bytes,ttl,immutable)
    }
    private fun insert(key:CacheKey,bytes:ByteArray,ttl:Long,metadata:DnsResponseMetadata?) {
        if(ttl<=0) return
        val now=nowMillis()
        cache.entries.removeAll {it.value.expires<=now}
        cache[key]=Record(bytes.copyOf(),now,now+ttl.coerceAtMost(600)*1000,metadata)
        while(cache.size>maxEntries) cache.remove(cache.keys.first())
    }
    private fun record(key:CacheKey):Record? {
        val r=cache[key] ?: return null
        if(nowMillis()>=r.expires) {cache.remove(key);return null}
        return r
    }
    private fun payload(record:Record,tx:Short):ByteArray {
        val b=record.bytes.copyOf()
        if(b.size>=2) {b[0]=(tx.toInt() ushr 8).toByte();b[1]=tx.toByte()}
        val elapsed=(nowMillis()-record.created).coerceAtLeast(0)/1000
        record.metadata?.ttlOffsets?.forEach { offset ->
            if(offset>=0 && offset+4<=b.size) {
                val original=(0..3).fold(0L) {v,i -> (v shl 8) or (b[offset+i].toLong() and 255)}
                val remaining=(original-elapsed).coerceAtLeast(0)
                for(i in 0..3) b[offset+i]=(remaining ushr (24-i*8)).toByte()
            }
        }
        return b
    }
    @Synchronized fun clear()=cache.clear()
}
