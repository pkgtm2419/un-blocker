package com.unblocker.ml.classifier

import android.content.Context
import com.unblocker.ml.analyzer.DomainFeatureExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TensorFlow Lite inference engine for on-device domain ad/tracker classification.
 *
 * ## Model contract
 * The bundled `domain_classifier.tflite` must expose:
 *   - **Input** : `[1, 9]` float32 tensor (matches [DomainFeatureExtractor.FEATURE_SIZE]).
 *   - **Output**: `[1, 1]` float32 tensor — probability that the domain is an ad/tracker.
 *
 * ## Thread safety
 * TFLite [Interpreter] is **not** thread-safe. The batch worker runs all inference calls
 * sequentially inside a single `Dispatchers.Default` coroutine, so no locking is needed.
 * If concurrent inference is ever required, create one interpreter per coroutine thread.
 *
 * ## Thermal throttle mitigation
 * [numThreads] is capped at 2. Higher thread counts increase peak CPU temperature during
 * the batch run and can cause Android's thermal daemon to throttle the device — which
 * paradoxically increases total wall-clock time for the batch.
 *
 * @param context  Application context used to open the assets folder.
 */
@Singleton
class TFLiteClassifierImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DomainClassifier {

    companion object {
        private const val MODEL_ASSET = "domain_classifier.tflite"
        private const val NUM_THREADS = 2
    }

    private var interpreter: Interpreter? = null

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        runCatching {
            val afd = context.assets.openFd(MODEL_ASSET)
            val fis = FileInputStream(afd.fileDescriptor)
            val channel = fis.channel
            val modelBuffer: ByteBuffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            val options = Interpreter.Options().apply {
                numThreads = NUM_THREADS
                useNNAPI = false          // NNAPI adds latency variance — keep deterministic
                useXNNPACK = true         // XNNPACK accelerates float32 inference on ARM
            }
            interpreter = Interpreter(modelBuffer, options)
        }.onFailure {
            // Model asset absent (e.g., first build before tflite is bundled) —
            // classifier returns 0f for all domains (no-op). Worker will retry next cycle.
            interpreter = null
        }
    }

    /**
     * Runs TFLite inference for [domain] and returns the ad-probability score.
     *
     * Returns **0.0f** if the interpreter is not initialised (model asset missing).
     * The batch worker treats 0.0f as "not an ad" — safe fail-open behaviour.
     */
    override fun predictAdProbability(domain: String): Float {
        val interp = interpreter ?: return 0f
        return runCatching {
            val features = DomainFeatureExtractor.extractFeatures(domain)
            val input  = arrayOf(features)                  // shape [1, 9]
            val output = Array(1) { FloatArray(1) }         // shape [1, 1]
            interp.run(input, output)
            output[0][0].coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }

    override fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
