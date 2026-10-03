package com.unblocker.app.services

import java.net.InetAddress

/** Serializes topology changes with delivery; snapshots never carry public fallback. */
class ResolverRegistry<K>(private val onChanged: () -> Unit) {
    data class Snapshot(val epoch:Long,val resolvers:List<InetAddress>)
    private val groups=LinkedHashMap<K,List<InetAddress>>()
    private var epoch=0L
    @Synchronized fun snapshot()=Snapshot(epoch,DnsResolverPolicy.selectConfigured(groups.values))
    @Synchronized fun update(network:K,resolvers:List<InetAddress>) {
        val sanitized=DnsResolverPolicy.sanitize(resolvers)
        if(groups[network]==sanitized) return
        groups[network]=sanitized
        changed()
    }
    @Synchronized fun remove(network:K) { if(groups.remove(network)!=null) changed() }
    @Synchronized fun clear() { if(groups.isNotEmpty()) { groups.clear();changed() } }
    @Synchronized fun useCurrent(snapshot:Snapshot,action:()->Unit):Boolean {
        if(snapshot.epoch!=epoch) return false
        action()
        return true
    }
    private fun changed() { epoch++;onChanged() }
}
