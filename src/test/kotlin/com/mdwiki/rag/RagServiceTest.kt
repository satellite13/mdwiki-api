package com.mdwiki.rag

import com.mdwiki.model.Page
import com.mdwiki.model.PageChunk
import com.mdwiki.repository.PageChunkRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.GraphService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RagServiceTest {

    @Mock private lateinit var pageChunkRepository: PageChunkRepository
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var embeddingProvider: EmbeddingProvider
    @Mock private lateinit var chunkingService: ChunkingService
    @Mock private lateinit var reranker: Reranker
    @Mock private lateinit var graphService: GraphService

    private lateinit var ragService: RagService

    @BeforeEach
    fun setUp() {
        ragService = RagService(pageChunkRepository, pageRepository, embeddingProvider, chunkingService, reranker, com.mdwiki.config.WikiProperties(), graphService)
    }

    @Test
    fun `indexPage creates chunks and stores embeddings`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "test", title = "Test", contentMd = "# Test\nContent")
        whenever(chunkingService.chunk("# Test\nContent")).thenReturn(
            listOf(ChunkingService.Chunk(0, "Content", "Test"))
        )
        whenever(pageChunkRepository.save(any<PageChunk>())).thenAnswer {
            val chunk = it.arguments[0] as PageChunk
            PageChunk(id = UUID.randomUUID(), page = chunk.page, chunkIndex = chunk.chunkIndex, chunkText = chunk.chunkText, sectionHeading = chunk.sectionHeading)
        }
        whenever(embeddingProvider.embed(listOf("Content"))).thenReturn(listOf(floatArrayOf(0.1f, 0.2f, 0.3f)))

        ragService.indexPage(page)

        verify(pageChunkRepository).deleteByPageId(pageId)
        verify(pageChunkRepository).save(any<PageChunk>())
        verify(pageChunkRepository).updateEmbedding(any(), any())
    }

    @Test
    fun `indexPage skips embedding when content is empty`() {
        val page = Page(id = UUID.randomUUID(), slug = "empty", title = "Empty", contentMd = "")
        whenever(chunkingService.chunk("")).thenReturn(emptyList())
        ragService.indexPage(page)
        verify(pageChunkRepository).deleteByPageId(page.id!!)
        verify(pageChunkRepository, never()).save(any<PageChunk>())
        verify(embeddingProvider, never()).embed(any<List<String>>())
    }

    @Test
    fun `deletePageChunks removes chunks for page`() {
        val pageId = UUID.randomUUID()
        ragService.deletePageChunks(pageId)
        verify(pageChunkRepository).deleteByPageId(pageId)
    }

    @Test
    fun `indexPage retries embedding provider and succeeds`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "retry-success", title = "Retry Success", contentMd = "Body")
        val savedChunkId = UUID.randomUUID()
        whenever(chunkingService.chunk("Body")).thenReturn(
            listOf(ChunkingService.Chunk(0, "Body", null))
        )
        whenever(pageChunkRepository.save(any<PageChunk>())).thenReturn(
            PageChunk(id = savedChunkId, page = page, chunkIndex = 0, chunkText = "Body", sectionHeading = null)
        )
        whenever(embeddingProvider.embed(listOf("Body")))
            .thenThrow(RuntimeException("transient-1"))
            .thenThrow(RuntimeException("transient-2"))
            .thenReturn(listOf(floatArrayOf(0.9f, 0.1f)))

        ragService.indexPage(page)

        verify(embeddingProvider, times(3)).embed(listOf("Body"))
        verify(pageChunkRepository).updateEmbedding(eq(savedChunkId), anyString())
    }

    @Test
    fun `indexPage stops when embedding retries exhausted`() {
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "retry-fail", title = "Retry Fail", contentMd = "Body")
        whenever(chunkingService.chunk("Body")).thenReturn(
            listOf(ChunkingService.Chunk(0, "Body", null))
        )
        whenever(pageChunkRepository.save(any<PageChunk>())).thenReturn(
            PageChunk(id = UUID.randomUUID(), page = page, chunkIndex = 0, chunkText = "Body", sectionHeading = null)
        )
        whenever(embeddingProvider.embed(listOf("Body"))).thenThrow(RuntimeException("always-fail"))

        ragService.indexPage(page)

        verify(embeddingProvider, times(3)).embed(listOf("Body"))
        verify(pageChunkRepository, never()).updateEmbedding(any(), any())
    }

    @Test
    fun `search falls back to base score when reranker throws`() {
        val pageId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "vector-page", title = "Vector Page", contentMd = "Vector body")
        whenever(embeddingProvider.embed("query")).thenReturn(floatArrayOf(0.2f, 0.8f))
        whenever(pageChunkRepository.findByVectorSimilarity(any(), any())).thenReturn(
            listOf(arrayOf(chunkId as Any, pageId as Any, 0 as Any, "Chunk text" as Any, "section" as Any, 0.91 as Any))
        )
        whenever(pageRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(page))
        whenever(pageRepository.fullTextSearch(eq("query"), any())).thenReturn(emptyList())
        whenever(graphService.getNeighborSlugs(any(), any())).thenReturn(emptySet())
        whenever(reranker.score("query", listOf("Chunk text"))).thenThrow(RuntimeException("reranker down"))

        val results = ragService.search("query", topK = 5)

        assertEquals(1, results.size)
        assertEquals("vector-page", results.first().pageSlug)
        assertEquals(0.91, results.first().score, 0.0001)
    }

    @Test
    fun `search falls back to base score when reranker returns mismatched scores`() {
        val pageId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "vector-page-2", title = "Vector Page 2", contentMd = "Vector body")
        whenever(embeddingProvider.embed("query")).thenReturn(floatArrayOf(0.5f, 0.5f))
        whenever(pageChunkRepository.findByVectorSimilarity(any(), any())).thenReturn(
            listOf(arrayOf(chunkId as Any, pageId as Any, 0 as Any, "Another chunk" as Any, "section" as Any, 0.77 as Any))
        )
        whenever(pageRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(page))
        whenever(pageRepository.fullTextSearch(eq("query"), any())).thenReturn(emptyList())
        whenever(graphService.getNeighborSlugs(any(), any())).thenReturn(emptySet())
        whenever(reranker.score("query", listOf("Another chunk"))).thenReturn(emptyList())

        val results = ragService.search("query", topK = 5)

        assertEquals(1, results.size)
        assertEquals("vector-page-2", results.first().pageSlug)
        assertEquals(0.77, results.first().score, 0.0001)
    }

    @Test
    fun `search still returns results when graph expansion fails`() {
        val pageId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "base-page", title = "Base Page", contentMd = "Body")
        whenever(embeddingProvider.embed("graph-test")).thenReturn(floatArrayOf(0.3f, 0.7f))
        whenever(pageChunkRepository.findByVectorSimilarity(any(), any())).thenReturn(
            listOf(arrayOf(chunkId as Any, pageId as Any, 0 as Any, "Base chunk" as Any, "section" as Any, 0.66 as Any))
        )
        whenever(pageRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(page))
        whenever(pageRepository.fullTextSearch(eq("graph-test"), any())).thenReturn(emptyList())
        whenever(graphService.getNeighborSlugs(any(), any())).thenThrow(RuntimeException("graph unavailable"))
        whenever(reranker.score("graph-test", listOf("Base chunk"))).thenReturn(listOf(0.42f))

        val results = ragService.search("graph-test", topK = 5)

        assertEquals(1, results.size)
        assertEquals("base-page", results.first().pageSlug)
        assertEquals(0.42, results.first().score, 0.0001)
    }

    @Test
    fun `search returns empty when topK is non-positive`() {
        assertTrue(ragService.search("query", topK = 0).isEmpty())
        verifyNoInteractions(embeddingProvider)
        verifyNoInteractions(pageChunkRepository)
    }

    @Test
    fun `search tolerates malformed vector rows and returns valid rows`() {
        val pageId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "valid-page", title = "Valid Page", contentMd = "Body")
        whenever(embeddingProvider.embed("query")).thenReturn(floatArrayOf(0.2f, 0.8f))
        whenever(pageChunkRepository.findByVectorSimilarity(any(), any())).thenReturn(
            listOf(
                arrayOf("bad-id" as Any, "bad-page-id" as Any, 0 as Any, "Bad chunk" as Any, "section" as Any, 0.1 as Any),
                arrayOf(chunkId as Any, pageId as Any, 1 as Any, "Good chunk" as Any, "section" as Any, 0.8 as Any)
            )
        )
        whenever(pageRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(page))
        whenever(pageRepository.fullTextSearch(eq("query"), any())).thenReturn(emptyList())
        whenever(graphService.getNeighborSlugs(any(), any())).thenReturn(emptySet())
        whenever(reranker.score("query", listOf("Good chunk"))).thenReturn(listOf(0.7f))

        val results = ragService.search("query", topK = 5)

        assertEquals(1, results.size)
        assertEquals("valid-page", results.first().pageSlug)
        assertEquals("Good chunk", results.first().chunkText)
    }

    @Test
    fun `search replaces non-finite reranker scores with base score`() {
        val pageId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "finite-fallback", title = "Finite Fallback", contentMd = "Body")
        whenever(embeddingProvider.embed("query")).thenReturn(floatArrayOf(0.4f, 0.6f))
        whenever(pageChunkRepository.findByVectorSimilarity(any(), any())).thenReturn(
            listOf(arrayOf(chunkId as Any, pageId as Any, 0 as Any, "Chunk finite" as Any, "section" as Any, 0.55 as Any))
        )
        whenever(pageRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(page))
        whenever(pageRepository.fullTextSearch(eq("query"), any())).thenReturn(emptyList())
        whenever(graphService.getNeighborSlugs(any(), any())).thenReturn(emptySet())
        whenever(reranker.score("query", listOf("Chunk finite"))).thenReturn(listOf(Float.NaN))

        val results = ragService.search("query", topK = 5)

        assertEquals(1, results.size)
        assertEquals(0.55, results.first().score, 0.0001)
    }
}
