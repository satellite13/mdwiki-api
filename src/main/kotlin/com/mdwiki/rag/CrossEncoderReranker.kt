package com.mdwiki.rag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path

interface Reranker {
    fun score(query: String, documents: List<String>): List<Float>
}

@Component
class CrossEncoderReranker : Reranker {

    private val log = LoggerFactory.getLogger(CrossEncoderReranker::class.java)
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()

    fun loadModel(modelPath: Path) {
        session = env.createSession(modelPath.toString())
        log.info("Cross-encoder model loaded from: $modelPath")
    }

    fun isLoaded(): Boolean = session != null

    override fun score(query: String, documents: List<String>): List<Float> {
        val sess = session
        if (sess == null) {
            log.warn("Cross-encoder model not loaded, returning uniform scores")
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
