package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.model.Tag
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PageSearchHit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SearchServiceTest {

    @Mock
    private lateinit var pageRepository: PageRepository

    @Mock
    private lateinit var ragService: RagService

    private lateinit var searchService: SearchService

    @BeforeEach
    fun setUp() {
        searchService = SearchService(pageRepository, ragService)
    }

    @Test
    fun `search returns mapped results`() {
        val id = UUID.randomUUID()
        val hit = mock<PageSearchHit> {
            on { getId() }.thenReturn(id)
            on { getSlug() }.thenReturn("result-page")
            on { getTitle() }.thenReturn("Result")
            on { getHeadline() }.thenReturn("Some 【matching】 content here")
        }
        whenever(pageRepository.searchWithHeadline("matching", 20)).thenReturn(listOf(hit))

        val results = searchService.search("matching")

        assertEquals(1, results.size)
        assertEquals("result-page", results[0].slug)
        assertEquals("Result", results[0].title)
        assertEquals(id, results[0].pageId)
        assertTrue(results[0].snippet.contains("matching"))
    }

    @Test
    fun `search returns empty for no matches`() {
        whenever(pageRepository.searchWithHeadline("nonexistent", 20)).thenReturn(emptyList())

        val results = searchService.search("nonexistent")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `ragSearch maps RAG hits with page tags`() {
        whenever(ragService.search("kotlin", 10)).thenReturn(
            listOf(
                RagService.SearchResult(
                    chunkText = "Kotlin is **great**",
                    sectionHeading = "Intro",
                    pageTitle = "Kotlin Guide",
                    pageSlug = "kotlin-guide",
                    score = 0.92
                )
            )
        )
        val kotlinTag = Tag(name = "kotlin")
        val page = Page(
            slug = "kotlin-guide",
            title = "Kotlin Guide",
            contentMd = "# Intro\nKotlin is **great**\n"
        ).apply {
            tags.add(kotlinTag)
        }
        whenever(pageRepository.findAllBySlugIn(listOf("kotlin-guide"))).thenReturn(listOf(page))

        val results = searchService.ragSearch("kotlin")

        assertEquals(1, results.size)
        assertEquals("kotlin-guide", results[0].pageSlug)
        assertEquals("Kotlin Guide", results[0].pageTitle)
        assertEquals("Intro", results[0].sectionHeading)
        assertEquals("intro", results[0].sectionKey)
        assertTrue(results[0].snippet.contains("Kotlin"))
        assertEquals(0.92, results[0].score)
        assertEquals(listOf("kotlin"), results[0].tags)
    }

    @Test
    fun `ragSearch disambiguates duplicate headings via chunk text`() {
        whenever(ragService.search("endpoint", 10)).thenReturn(
            listOf(
                RagService.SearchResult(
                    chunkText = "second endpoint",
                    sectionHeading = "API",
                    pageTitle = "Guide",
                    pageSlug = "guide",
                    score = 0.8
                )
            )
        )
        whenever(pageRepository.findAllBySlugIn(listOf("guide"))).thenReturn(
            listOf(
                Page(
                    slug = "guide",
                    title = "Guide",
                    contentMd = "# Intro\n\n## API\nfirst endpoint\n\n# Other\n\n## API\nsecond endpoint\n"
                )
            )
        )

        val results = searchService.ragSearch("endpoint")

        assertEquals("other/api", results[0].sectionKey)
    }

    @Test
    fun `ragSearch returns empty for blank query`() {
        assertTrue(searchService.ragSearch("  ").isEmpty())
    }
}
