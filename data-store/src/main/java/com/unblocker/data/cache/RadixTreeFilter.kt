package com.unblocker.data.cache

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact domain and suffix matching through a reverse-label trie.
 *
 * Loading builds a new root before assigning it, but publication is not synchronized or volatile.
 * Callers must coordinate loading with readers rather than assuming concurrent reload safety.
 */
@Singleton
class RadixTreeFilter
    @Inject
    constructor() {
        private var root: TrieNode = TrieNode()

        /**
         * Replaces the trie after lowercasing keys and trimming trailing dots.
         *
         * @param domains Complete replacement set; callers must coordinate concurrent access.
         */
        fun loadDomains(domains: Collection<String>) {
            val newRoot = TrieNode()
            for (domain in domains) {
                // Insert labels in reverse order: "ads.example.com" → [com, example, ads]
                val labels =
                    domain
                        .lowercase()
                        .trimEnd('.')
                        .split('.')
                        .reversed()
                var node = newRoot
                for (label in labels) {
                    node = node.children.getOrPut(label) { TrieNode() }
                }
                node.isTerminal = true
            }
            root = newRoot
        }

        /**
         * Checks whether an exact key or a parent suffix is terminal in the trie.
         *
         * @param domain Domain lowercased and stripped of trailing dots before lookup.
         * @return Whether an exact or suffix rule matches.
         */
        fun contains(domain: String): Boolean {
            val labels =
                domain
                    .lowercase()
                    .trimEnd('.')
                    .split('.')
                    .reversed()
            var node = root
            for (label in labels) {
                if (node.isTerminal) return true // Wildcard suffix hit
                node = node.children[label] ?: return false
            }
            return node.isTerminal
        }

        /**
         * Counts terminal nodes in the current trie.
         *
         * @return Number of unique normalized rule keys.
         */
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
