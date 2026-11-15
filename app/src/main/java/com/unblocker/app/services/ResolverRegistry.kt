package com.unblocker.app.services

import java.net.InetAddress

/**
 * Serializes configured-resolver topology changes and epoch-guarded operations.
 *
 * @param K Network identity type.
 * @param onChanged Callback invoked under the registry monitor after a topology change.
 */
class ResolverRegistry<K>(
    private val onChanged: () -> Unit,
) {
    /**
     * Configured resolver view at one topology generation.
     *
     * @property epoch Registry topology generation.
     * @property resolvers Sanitized configured resolvers, without a public fallback.
     */
    data class Snapshot(
        val epoch: Long,
        val resolvers: List<InetAddress>,
    )

    private val groups = LinkedHashMap<K, List<InetAddress>>()
    private var epoch = 0L
    /**
     * Returns the current topology generation and sanitized resolver list.
     */

    /**
     * Replaces resolver candidates for a network and advances the epoch when the sanitized list changes.
     *
     * @param network Network identity used as a registry key.
     * @param resolvers Android-configured resolver addresses.
     */
    @Synchronized fun snapshot() = Snapshot(epoch, DnsResolverPolicy.selectConfigured(groups.values))

    @Synchronized fun update(
        network: K,
        resolvers: List<InetAddress>,
    ) {
        val sanitized = DnsResolverPolicy.sanitize(resolvers)
        if (groups[network] == sanitized) return
        groups[network] = sanitized
        changed()
    }
    /**
     * Removes a network and advances the epoch if it was registered.
     *
     * @param network Network identity to remove.
     */

    /**
     * Clears configured networks, notifying the callback only when the registry was nonempty.
     */
    @Synchronized fun remove(network: K) {
        if (groups.remove(network) != null) changed()
    }

    /**
     * Runs an operation under the registry monitor only while the snapshot epoch is current.
     *
     * @param snapshot Previously obtained topology snapshot.
     * @param action Operation to execute while topology updates are excluded.
     * @return Whether action ran; exceptions from action propagate.
     */
    @Synchronized fun clear() {
        if (groups.isNotEmpty()) {
            groups.clear()
            changed()
        }
    }

    @Synchronized fun useCurrent(
        snapshot: Snapshot,
        action: () -> Unit,
    ): Boolean {
        if (snapshot.epoch != epoch) return false
        action()
        return true
    }

    private fun changed() {
        epoch++
        onChanged()
    }
}
