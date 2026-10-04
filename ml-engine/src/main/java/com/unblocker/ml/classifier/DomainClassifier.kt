package com.unblocker.ml.classifier

/**
 * Contract for an on-device domain classification model.
 *
 * Separates the TFLite implementation from its callers so that:
 *  - Unit tests can inject a deterministic fake without a real `.tflite` file.
 *  - Future model backends (ONNX, Gemini Nano) can be swapped in without changing
 *    [BatchDomainAnalysisWorker].
 */
interface DomainClassifier {

    /**
     * Predicts the probability (0.0–1.0) that [domain] is an ad/tracker domain.
     *
     * @param domain  Fully-qualified domain name (lowercased).
     * @return        Confidence score in [0.0, 1.0].
     *                Returns 0.0f if the model is not ready or an error occurs.
     */
    fun predictAdProbability(domain: String): Float

    /**
     * Releases native resources held by the model interpreter.
     * Must be called when the owning component is destroyed.
     */
    fun close()
}
