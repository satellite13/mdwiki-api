package com.mdwiki.rag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

interface Reranker {
    fun score(query: String, documents: List<String>): List<Float>
}

@Component
class CrossEncoderReranker : Reranker {

    private val log = LoggerFactory.getLogger(CrossEncoderReranker::class.java)
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private val warnedNotLoaded = AtomicBoolean(false)

    @Value("\${mdwiki.rag.reranker.model-path:}")
    private lateinit var configuredModelPath: String

    @PostConstruct
    fun init() {
        val rawPath = configuredModelPath.trim()
        if (rawPath.isEmpty()) {
            log.info("Cross-encoder reranker model path is not configured; fallback scoring will be used")
            return
        }

        val modelPath = Paths.get(rawPath)
        if (!Files.exists(modelPath)) {
            log.warn("Cross-encoder model file not found at '{}'; fallback scoring will be used", modelPath)
            return
        }

        try {
            loadModel(modelPath)
        } catch (e: Exception) {
            log.warn("Failed to load cross-encoder model from '{}': {}", modelPath, e.message)
        }
    }

    fun loadModel(modelPath: Path) {
        session = env.createSession(modelPath.toString())
        log.info("Cross-encoder model loaded from: $modelPath")
    }

    fun isLoaded(): Boolean = session != null

    override fun score(query: String, documents: List<String>): List<Float> {
        val sess = session
        if (sess == null) {
            if (warnedNotLoaded.compareAndSet(false, true)) {
                log.warn("Cross-encoder model not loaded, returning uniform scores")
            }
            return documents.map { 0.5f }
        }
        return documents.map { doc -> scoreSingle(sess, query, doc) }
    }

    private fun scoreSingle(sess: OrtSession, query: String, document: String): Float {
        val inputText = "$query [SEP] $document"
        val inputIds = simpleTokenize(inputText)
        val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(inputIds))
        val attentionMask = LongArray(inputIds.size) { 1L }
        val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMask))
        val inputs = mapOf("input_ids" to inputIdsTensor, "attention_mask" to attentionMaskTensor)
        return try {
            val result = sess.run(inputs)
            val outputTensor = result[0] as OnnxTensor
            outputTensor.floatBuffer.get(0)
        } catch (e: Exception) {
            log.error("Cross-encoder scoring failed", e)
            0.0f
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
        }
    }

    private fun simpleTokenize(text: String): LongArray {
        val tokens = text.split(" ").take(512)
        return LongArray(tokens.size) { tokens[it].hashCode().toLong() and 0xFFFFL }
    }
}
