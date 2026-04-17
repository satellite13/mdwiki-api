package com.mdwiki.rag

import com.mdwiki.config.WikiProperties
import com.mdwiki.model.Page
import com.mdwiki.model.PageChunk
import com.mdwiki.repository.PageChunkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.GraphService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RagService(
    private val pageChunkRepository: PageChunkRepository,
    private val pageRepository: PageRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val chunkingService: ChunkingService,
    private val reranker: Reranker,
    private val wikiProperties: WikiProperties,
    private val graphService: GraphService
) {

    private val log = LoggerFactory.getLogger(RagService::class.java)

    data class ChunkCandidate(
        val chunkId: UUID, val pageId: UUID, val chunkText: String,
        val sectionHeading: String?, val pageTitle: String, val pageSlug: String, val score: Double
    )

    data class SearchResult(
        val chunkText: String, val sectionHeading: String?,
        val pageTitle: String, val pageSlug: String, val score: Double
    )

    @Transactional
    fun indexPage(page: Page) {
        val startedAt = System.nanoTime()
        val pageId = page.id ?: return
        val content = page.contentMd ?: ""
        pageChunkRepository.deleteByPageId(pageId)
        val chunks = chunkingService.chunk(content)
        if (chunks.isEmpty()) return

        val savedChunks = chunks.map { chunk ->
            pageChunkRepository.save(PageChunk(
                page = page, chunkIndex = chunk.index,
                chunkText = chunk.text, sectionHeading = chunk.sectionHeading
            ))
        }

        val texts = savedChunks.map { it.chunkText }
        val embeddings = embedForIndexWithRetry(texts, page.slug) ?: return
        if (embeddings.size != savedChunks.size) {
            log.error(
                "Embedding size mismatch for page '{}': chunks={}, embeddings={}, elapsedMs={}",
                page.slug,
                savedChunks.size,
                embeddings.size,
                elapsedMs(startedAt)
            )
            return
        }

        for (i in savedChunks.indices) {
            val embeddingStr = "[${embeddings[i].joinToString(",")}]"
            pageChunkRepository.updateEmbedding(savedChunks[i].id!!, embeddingStr)
        }
        log.info(
            "Indexed {} chunks for page '{}' in {} ms",
            savedChunks.size,
            page.slug,
            elapsedMs(startedAt)
        )
    }

    @Transactional
    fun deletePageChunks(pageId: UUID) {
        pageChunkRepository.deleteByPageId(pageId)
    }

    fun search(query: String, topK: Int = 10): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        if (topK <= 0) return emptyList()
        val startedAt = System.nanoTime()
        val configuredVectorLimit = wikiProperties.rag.vectorSearchLimit
        val vectorSearchLimit = configuredVectorLimit.coerceAtLeast(1)
        if (configuredVectorLimit <= 0) {
            log.warn(
                "Invalid mdwiki.rag.vectorSearchLimit={} in config; using {}",
                configuredVectorLimit,
                vectorSearchLimit
            )
        }

        val vectorCandidates = fetchVectorCandidates(query, vectorSearchLimit)
        val ftsCandidates = fetchFtsCandidates(query, vectorSearchLimit)
        var allCandidates = (vectorCandidates + ftsCandidates).distinctBy { it.chunkId }

        if (allCandidates.isEmpty()) {
            log.info("RAG search returned no candidates for query='{}' (elapsedMs={})", query, elapsedMs(startedAt))
            return emptyList()
        }
        allCandidates = (allCandidates + fetchGraphCandidates(allCandidates)).distinctBy { it.chunkId }

        // Cap candidates before reranking to limit compute cost
        val cappedCandidates = allCandidates.take(minOf(topK * 3, allCandidates.size))
        val scores = rerankOrFallback(query, cappedCandidates)
        val results = cappedCandidates.indices
            .map { index ->
                val candidate = cappedCandidates[index]
                SearchResult(
                    chunkText = candidate.chunkText,
                    sectionHeading = candidate.sectionHeading,
                    pageTitle = candidate.pageTitle,
                    pageSlug = candidate.pageSlug,
                    score = scores[index]
                )
            }
            .sortedByDescending { it.score }
            .take(topK)

        log.info(
            "RAG search query='{}' completed in {} ms (vector={}, fts={}, totalDistinct={}, reranked={}, returned={})",
            query,
            elapsedMs(startedAt),
            vectorCandidates.size,
            ftsCandidates.size,
            allCandidates.size,
            cappedCandidates.size,
            results.size
        )
        return results
    }

    private fun fetchVectorCandidates(query: String, vectorSearchLimit: Int): List<ChunkCandidate> {
        return try {
            val queryEmbedding = embeddingProvider.embed(query)
            val embeddingStr = "[${queryEmbedding.joinToString(",")}]"
            val rawResults = pageChunkRepository.findByVectorSimilarity(embeddingStr, vectorSearchLimit)
            // Batch-load pages to avoid N+1 queries
            val pageIds = rawResults.mapNotNull { row -> row.getOrNull(1) as? UUID }.distinct()
            val pagesMap = pageRepository.findAllById(pageIds).mapNotNull { page ->
                page.id?.let { id -> id to page }
            }.toMap()
            rawResults.mapNotNull { row -> rowToCandidate(row, pagesMap) }
        } catch (e: Exception) {
            log.error("Vector search failed for query '{}': {}", query, e.message)
            emptyList()
        }
    }

    private fun fetchFtsCandidates(query: String, limit: Int): List<ChunkCandidate> {
        val ftsPages = pageRepository.fullTextSearch(query, limit)
        if (ftsPages.isEmpty()) return emptyList()
        val pagesById = ftsPages.mapNotNull { page -> page.id?.let { id -> id to page } }.toMap()
        if (pagesById.isEmpty()) return emptyList()
        val ftsChunks = pagesById.keys.flatMap { pageId ->
            pageChunkRepository.findByPageIdOrderByChunkIndex(pageId)
        }
        return ftsChunks.mapNotNull { chunk ->
            val page = pagesById[chunk.page.id] ?: return@mapNotNull null
            val pageId = page.id ?: return@mapNotNull null
            val chunkId = chunk.id ?: return@mapNotNull null
            ChunkCandidate(
                chunkId = chunkId,
                pageId = pageId,
                chunkText = chunk.chunkText,
                sectionHeading = chunk.sectionHeading,
                pageTitle = page.title,
                pageSlug = page.slug,
                score = 0.5
            )
        }
    }

    private fun fetchGraphCandidates(seedCandidates: List<ChunkCandidate>): List<ChunkCandidate> {
        if (seedCandidates.isEmpty()) return emptyList()
        return try {
            val hitPageSlugs = seedCandidates.map { it.pageSlug }.distinct()
            val neighborSlugs = mutableSetOf<String>()
            for (slug in hitPageSlugs.take(5)) { // expand top-5 hit pages
                neighborSlugs.addAll(graphService.getNeighborSlugs(slug, 1))
            }
            neighborSlugs.removeAll(hitPageSlugs.toSet()) // don't re-add pages already in results
            if (neighborSlugs.isEmpty()) return emptyList()

            val neighborPages = pageRepository.findAllBySlugIn(neighborSlugs.toList())
            val pagesById = neighborPages.mapNotNull { page -> page.id?.let { id -> id to page } }.toMap()
            if (pagesById.isEmpty()) return emptyList()

            val neighborChunks = pageChunkRepository.findByPageIdIn(pagesById.keys.toList())
            neighborChunks.mapNotNull { chunk ->
                val page = pagesById[chunk.page.id] ?: return@mapNotNull null
                val pageId = page.id ?: return@mapNotNull null
                val chunkId = chunk.id ?: return@mapNotNull null
                ChunkCandidate(
                    chunkId = chunkId,
                    pageId = pageId,
                    chunkText = chunk.chunkText,
                    sectionHeading = chunk.sectionHeading,
                    pageTitle = page.title,
                    pageSlug = page.slug,
                    score = 0.3 // lower base score for graph-expanded results
                )
            }
        } catch (e: Exception) {
            log.warn("Graph expansion failed: {}", e.message)
            emptyList()
        }
    }

    private fun rerankOrFallback(query: String, candidates: List<ChunkCandidate>): List<Double> {
        if (candidates.isEmpty()) return emptyList()
        val fallback = candidates.map { it.score }
        return try {
            val scores = reranker.score(query, candidates.map { it.chunkText })
            if (scores.size != candidates.size) {
                log.warn(
                    "Reranker size mismatch for query '{}': candidates={}, scores={}, fallback=base-score",
                    query,
                    candidates.size,
                    scores.size
                )
                return fallback
            }
            scores.mapIndexed { index, score ->
                val scoreDouble = score.toDouble()
                if (scoreDouble.isFinite()) scoreDouble else fallback[index]
            }
        } catch (e: Exception) {
            log.warn("Reranker failed for query '{}': {}. Using base-score fallback.", query, e.message)
            fallback
        }
    }

    private fun rowToCandidate(row: Array<Any>, pagesMap: Map<UUID, Page>): ChunkCandidate? {
        return try {
            val chunkId = row[0] as UUID
            val pageId = row[1] as UUID
            val chunkText = row[3] as String
            val sectionHeading = row[4] as? String
            val score = (row[5] as Number).toDouble()
            val page = pagesMap[pageId] ?: return null
            ChunkCandidate(chunkId = chunkId, pageId = pageId, chunkText = chunkText,
                sectionHeading = sectionHeading, pageTitle = page.title, pageSlug = page.slug, score = score)
        } catch (e: Exception) {
            log.error("Failed to parse vector search row: ${e.message}")
            null
        }
    }

    private fun embedForIndexWithRetry(texts: List<String>, pageSlug: String): List<FloatArray>? {
        val attempts = wikiProperties.rag.embeddingIndexAttempts.coerceAtLeast(1)
        repeat(attempts) { attemptIndex ->
            val attempt = attemptIndex + 1
            try {
                return embeddingProvider.embed(texts)
            } catch (e: Exception) {
                if (attempt == attempts) {
                    log.error(
                        "Failed to generate embeddings for page '{}' after {} attempts: {}",
                        pageSlug,
                        attempt,
                        e.message
                    )
                    return null
                }
                log.warn(
                    "Embedding attempt {}/{} failed for page '{}': {}",
                    attempt,
                    attempts,
                    pageSlug,
                    e.message
                )
            }
        }
        return null
    }

    private fun elapsedMs(startedAtNanos: Long): Long {
        return (System.nanoTime() - startedAtNanos) / 1_000_000
    }
}
