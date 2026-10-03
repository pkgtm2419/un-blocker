package com.unblocker.app.services

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class ResolverRegistryTest {
    @Test fun replacementAndLossInvalidateCacheAndRejectOldDelivery() {
        var invalidations=0
        val registry=ResolverRegistry<String> { invalidations++ }
        assertTrue(registry.snapshot().resolvers.isEmpty())
        registry.update("wifi",listOf(InetAddress.getByName("192.0.2.1")))
        val old=registry.snapshot()
        registry.update("wifi",listOf(InetAddress.getByName("192.0.2.1")))
        assertEquals(1,invalidations)
        registry.update("wifi",listOf(InetAddress.getByName("2001:db8::1")))
        var delivered=false
        assertFalse(registry.useCurrent(old) { delivered=true })
        assertFalse(delivered)
        assertTrue(registry.useCurrent(registry.snapshot()) { delivered=true })
        registry.remove("wifi")
        assertTrue(registry.snapshot().resolvers.isEmpty())
        assertEquals(3,invalidations)
    }
    @Test fun networkIdentityChangeEvenWithSameDnsRejectsOldWork() {
        val registry=ResolverRegistry<String> {}
        val servers=listOf(InetAddress.getByName("192.0.2.1"))
        registry.update("wifi",servers)
        val old=registry.snapshot()
        registry.update("mobile",servers)
        assertFalse(registry.useCurrent(old) { fail("old network delivery") })
        registry.clear()
        assertTrue(registry.snapshot().resolvers.isEmpty())
    }
}
