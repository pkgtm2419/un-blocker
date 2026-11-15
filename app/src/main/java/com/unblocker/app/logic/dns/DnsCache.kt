package com.unblocker.app.logic.dns

import com.unblocker.app.services.ValidatedDnsResponse
import java.util.Collections

/**
 * Bounded volatile DNS cache; callers must evaluate current policy before delivery.
 *
 * @param maxEntries Maximum retained records; must be positive.
 * @param nowMillis Monotonic clock in milliseconds used for expiry and TTL aging.
 * @throws IllegalArgumentException If maxEntries is not positive.
 */
class DnsCache(
    private val maxEntries:Int=1000,
    private val nowMillis:()->Long={System.nanoTime()/1_000_000}
) {
    private data class CacheKey(val domain:String,val type:Short,val clazz:Short,val ipv6:Boolean,val variant:String="")
    private data class Record(val bytes:ByteArray,val created:Long,val expires:Long,val metadata:DnsResponseMetadata?)
    private val cache=LinkedHashMap<CacheKey,Record>(16,.75f,true)
    init {require(maxEntries>0)}

    /**
     * Looks up a compatibility cache entry and copies its bytes with the requested transaction ID.
     *
     * @param domain Cache hostname key, used without normalization.
     * @param queryType DNS QTYPE wire bits.
     * @param transactionId Client transaction-ID wire bits.
     * @param queryClass DNS QCLASS wire bits.
     * @param isIpv6 Whether the client transport uses IPv6.
     * @return Copied payload, or null when missing or expired.
     */
    @Synchronized fun get(domain:String,queryType:Short,transactionId:Short,
        queryClass:Short=1,isIpv6:Boolean=false):ByteArray? =
        record(CacheKey(domain,queryType,queryClass,isIpv6))?.let { payload(it,transactionId) }

    /**
     * Retrieves cached metadata and copies bytes with aged TTLs and the client transaction ID.
     *
     * @param query Query including cache variant and cache-permission state.
     * @return Cached validated response, or null when absent, expired, disabled or lacking metadata.
     */
    @Synchronized internal fun getValidated(query:DnsQuery):ValidatedDnsResponse? {
        if(!query.cacheAllowed) return null
        val record=record(CacheKey(query.domain,query.queryType,query.queryClass,query.isIpv6,query.cacheVariant)) ?: return null
        val metadata=record.metadata ?: return null
        val elapsed=((nowMillis()-record.created).coerceAtLeast(0)/1000)
        return ValidatedDnsResponse(payload(record,query.transactionId),metadata.copy(
            transactionId=query.transactionId.toInt() and 0xffff,
            minPositiveTtlSeconds=metadata.minPositiveTtlSeconds?.let {(it-elapsed).coerceAtLeast(0)}))
    }
    /**
     * Copies a compatibility payload into the cache when its TTL is positive.
     *
     * @param domain Hostname cache key, used without normalization.
     * @param queryType DNS QTYPE wire bits.
     * @param dnsPayload DNS body to copy; this overload does not validate it.
     * @param ttlSeconds Lifetime in seconds, capped to 600 seconds.
     * @param queryClass DNS QCLASS wire bits.
     * @param isIpv6 Whether the client transport uses IPv6.
     */
    @Synchronized fun put(domain:String,queryType:Short,dnsPayload:ByteArray,ttlSeconds:Long,
        queryClass:Short=1,isIpv6:Boolean=false) {
        insert(CacheKey(domain,queryType,queryClass,isIpv6),dnsPayload,ttlSeconds,null)
    }
    /**
     * Caches a positive, complete matching response when metadata supplies a positive TTL.
     *
     * @param query Query defining identity, variant and caching permission.
     * @param response Previously validated upstream bytes and metadata, copied for cache ownership.
     */
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
        if (cache.size >= maxEntries) {
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        cache[key]=Record(bytes.copyOf(),now,now+ttl.coerceAtMost(600)*1000,metadata)
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
    /**
     * Removes all retained DNS records.
     */
    @Synchronized fun clear()=cache.clear()
}
