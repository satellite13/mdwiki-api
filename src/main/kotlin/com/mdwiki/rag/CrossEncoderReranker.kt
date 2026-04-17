package com.mdwiki.rag

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp

interface Reranker {
    fun score(query: String, documents: List<String>): List<Float>
}

/**
 * Cross-encoder реранкер поверх `cross-encoder/ms-marco-MiniLM-L-6-v2` (BERT-base, 22 MB после INT8).
 *
 * Модель принимает пары (query, document) и возвращает один logit — оценку релевантности.
 * Для совместимости с остальным pipeline (fts/vector score ∈ [0,1]) применяем sigmoid.
 *
 * Токенизация делается настоящим HuggingFaceTokenizer (WordPiece BERT), упакованным в DJL JNI.
 * Файл `tokenizer.json` той же модели лежит рядом с ONNX в classpath.
 */
@Component
class CrossEncoderReranker : Reranker {

    private val log = LoggerFactory.getLogger(CrossEncoderReranker::class.java)
    private var session: OrtSession? = null
    private var tokenizer: HuggingFaceTokenizer? = null
    private val env = OrtEnvironment.getEnvironment()
    private val warnedNotLoaded = AtomicBoolean(false)

    @Value("\${mdwiki.rag.reranker.model-path:}")
    private lateinit var configuredModelPath: String

    @Value("\${mdwiki.rag.reranker.tokenizer-path:}")
    private lateinit var configuredTokenizerPath: String

    @Value("\${mdwiki.rag.reranker.max-length:512}")
    private var maxLength: Int = 512

    private companion object {
        private const val MODEL_CLASSPATH_RESOURCE = "models/cross-encoder/model_quantized.onnx"
        private const val TOKENIZER_CLASSPATH_RESOURCE = "models/cross-encoder/tokenizer.json"
    }

    @PostConstruct
    fun init() {
        val modelPath = resolveModelPath() ?: return
        val tok = resolveTokenizer()
        if (tok == null) {
            log.warn(
                "Cross-encoder tokenizer недоступен (classpath '{}' / MDWIKI_RERANKER_TOKENIZER_PATH); " +
                    "fallback-скоры будут использованы",
                TOKENIZER_CLASSPATH_RESOURCE
            )
            return
        }
        try {
            session = env.createSession(modelPath.toString())
            tokenizer = tok
            log.info("Cross-encoder reranker ready: model='{}', maxLength={}", modelPath, maxLength)
        } catch (e: Exception) {
            log.warn("Failed to load cross-encoder model from '{}': {}", modelPath, e.message, e)
            tok.close()
        }
    }

    @PreDestroy
    fun shutdown() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        try {
            tokenizer?.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Источники ONNX-модели по приоритету:
     *  1. `MDWIKI_RERANKER_MODEL_PATH` — абсолютный/относительный путь к .onnx.
     *  2. classpath-ресурс [MODEL_CLASSPATH_RESOURCE] — модель из jar (см. build.gradle.kts).
     *
     * ONNX Runtime требует реальный file path, поэтому classpath-ресурс извлекается в tmpdir.
     */
    private fun resolveModelPath(): Path? {
        val rawPath = configuredModelPath.trim()
        if (rawPath.isNotEmpty()) {
            val modelPath = Paths.get(rawPath)
            if (!Files.exists(modelPath)) {
                log.warn("Cross-encoder model file not found at '{}'; fallback scoring will be used", modelPath)
                return null
            }
            return modelPath
        }

        val resource = ClassPathResource(MODEL_CLASSPATH_RESOURCE)
        if (!resource.exists()) {
            log.info(
                "Cross-encoder reranker not configured and classpath resource '{}' is absent; " +
                    "fallback scoring will be used",
                MODEL_CLASSPATH_RESOURCE
            )
            return null
        }

        return try {
            val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "mdwiki-rag")
            Files.createDirectories(tmpDir)
            val target = tmpDir.resolve("cross-encoder.onnx")
            resource.inputStream.use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
            log.info("Cross-encoder model extracted from classpath to '{}'", target)
            target
        } catch (e: Exception) {
            log.warn(
                "Failed to extract cross-encoder model from classpath resource '{}': {}",
                MODEL_CLASSPATH_RESOURCE,
                e.message
            )
            null
        }
    }

    /**
     * Источники tokenizer.json по приоритету:
     *  1. `MDWIKI_RERANKER_TOKENIZER_PATH` — путь к файлу.
     *  2. classpath-ресурс [TOKENIZER_CLASSPATH_RESOURCE] (упакован в jar).
     *
     * DJL HuggingFaceTokenizer грузит JNI нативку при первом обращении; при отсутствии
     * кэшированного `libtokenizers.so` скачивает с publish.djl.ai.
     */
    private fun resolveTokenizer(): HuggingFaceTokenizer? {
        // truncation=LONGEST_FIRST (по умолчанию в HF) — при превышении лимита режется
        // самая длинная из двух последовательностей, [CLS]/[SEP] сохраняются.
        val opts = mapOf(
            "addSpecialTokens" to "true",
            "padding" to "false",
            "truncation" to "LONGEST_FIRST",
            "maxLength" to maxLength.toString()
        )

        val rawPath = configuredTokenizerPath.trim()
        if (rawPath.isNotEmpty()) {
            val p = Paths.get(rawPath)
            if (!Files.exists(p)) {
                log.warn("Tokenizer file not found at '{}'; fallback to classpath resource", p)
            } else {
                return try {
                    HuggingFaceTokenizer.newInstance(p, opts)
                } catch (e: Exception) {
                    log.warn("Failed to load tokenizer from '{}': {}", p, e.message, e)
                    null
                }
            }
        }

        val resource = ClassPathResource(TOKENIZER_CLASSPATH_RESOURCE)
        if (!resource.exists()) return null

        return try {
            resource.inputStream.use { input ->
                HuggingFaceTokenizer.newInstance(input, opts)
            }
        } catch (e: Exception) {
            log.warn("Failed to load tokenizer from classpath '{}': {}", TOKENIZER_CLASSPATH_RESOURCE, e.message, e)
            null
        }
    }

    fun isLoaded(): Boolean = session != null && tokenizer != null

    override fun score(query: String, documents: List<String>): List<Float> {
        val sess = session
        val tok = tokenizer
        if (sess == null || tok == null) {
            if (warnedNotLoaded.compareAndSet(false, true)) {
                log.warn("Cross-encoder model/tokenizer not loaded, returning uniform fallback scores")
            }
            return documents.map { 0.5f }
        }
        return documents.map { doc -> scoreSingle(sess, tok, query, doc) }
    }

    private fun scoreSingle(sess: OrtSession, tok: HuggingFaceTokenizer, query: String, document: String): Float {
        val encoding: Encoding = tok.encode(query, document)
        val ids = encoding.ids
        val mask = encoding.attentionMask
        val types = encoding.typeIds

        val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(ids))
        val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(mask))
        val tokenTypeIdsTensor = OnnxTensor.createTensor(env, arrayOf(types))
        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor,
        )
        return try {
            sess.run(inputs).use { result ->
                val outputTensor = result[0] as OnnxTensor
                val logit = outputTensor.floatBuffer.get(0)
                sigmoid(logit)
            }
        } catch (e: Exception) {
            log.error("Cross-encoder scoring failed", e)
            Float.NaN
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    private fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
}
