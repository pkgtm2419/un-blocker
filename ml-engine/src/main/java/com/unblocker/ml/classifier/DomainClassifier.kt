package com.unblocker.ml.classifier

/**
 * On-device domain scoring boundary used by the separate library batch-analysis worker.
 */
interface DomainClassifier {
    /**
     * Scores whether a domain resembles advertising or tracking infrastructure.
     *
     * @param domain Domain expected to be normalized by the caller.
     * @return Confidence score conventionally from 0 to 1; failure handling is implementation-specific.
     */
    fun predictAdProbability(domain: String): Float

    /**
     * Releases resources owned by the classifier; implementations without resources may do nothing.
     */
    fun close()
}
