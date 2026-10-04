package com.unblocker.data.cache

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Radix Tree (Trie) implementation for exact and suffix-based domain matching.
 *
 * Complements the [BloomFilterManager] for cases where a precise false-positive-free
 * answer is needed (e.g., verifying a Bloom Filter hit before writing a block rule).
 *
 * ## When to use
 * - During ML batch analysis: verify that a domain suspected by the Bloom Filter is
 *   genuinely blocked (eliminates false positives before DB writes).
 * - For diagnostic tooling: count exact match coverage.
 *
 * ## Thread safety
 * The trie is rebuilt atomically via [loadDomains] and thereafter read-only.
 * Concurrent reads are safe without locking.
 */
@Singleton
class RadixTreeFilter @Inject constructor() {

    private var root: TrieNode = TrieNode()

    /** Replaces the entire trie with [domains]. Not thread-safe during load. */
    fun loadDomains(domains: Collection<String>) {
        val newRoot = TrieNode()
        for (domain in domains) {
            // Insert labels in reverse order: "ads.example.com" → [com, example, ads]
            val labels = domain.lowercase().trimEnd('.').split('.').reversed()
            var node = newRoot
            for (label in labels) {
                node = node.children.getOrPut(label) { TrieNode() }
            }
            node.isTerminal = true
        }
        root = newRoot
    }

    /**
     * Returns true if [domain] matches any exact or wildcard (suffix) rule in the trie.
     *
     * A parent node marked `isTerminal = true` means all subdomains also match
     * (e.g., inserting "example.com" blocks "sub.example.com").
     */
    fun contains(domain: String): Boolean {
        val labels = domain.lowercase().trimEnd('.').split('.').reversed()
        var node = root
        for (label in labels) {
            if (node.isTerminal) return true  // Wildcard suffix hit
            node = node.children[label] ?: return false
        }
        return node.isTerminal
    }

    /** Returns the total number of unique domain rules loaded. */
    fun size(): Int {
        var count = 0
        fun traverse(node: TrieNode) {
            if (node.isTerminal) count++
            node.children.values.forEach(::traverse)
        }
        traverse(root)
        return count
    }

    private class TrieNode {
        val children = HashMap<String, TrieNode>(4)
        var isTerminal = false
    }
}
