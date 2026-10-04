package com.unblocker.common.model

/**
 * Identifies the origin source of a blocking or allow rule.
 */
enum class RuleSource {
    /** Compiled from static blocklist assets bundled with the app. */
    STATIC,
    /** Inferred by the on-device TensorFlow Lite ML batch worker. */
    ML,
    /** Manually set by the user via the whitelist / false-positive dashboard. */
    USER
}
