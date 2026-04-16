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
}
