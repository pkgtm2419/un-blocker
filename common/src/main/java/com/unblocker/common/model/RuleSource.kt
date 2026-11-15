package com.unblocker.common.model

/**
 * Identifies the origin source of a blocking or allow rule.
 */
enum class RuleSource {
    /** Compiled from static blocklist assets bundled with the app. */
    STATIC,
    /** Inferred by an on-device classifier or heuristic worker. */
    ML,
    /** Manually set by the user via the whitelist / false-positive dashboard. */
    USER
}
