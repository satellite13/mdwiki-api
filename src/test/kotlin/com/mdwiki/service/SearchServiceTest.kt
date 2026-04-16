package com.mdwiki.service

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

    private lateinit var searchService: SearchService

    @BeforeEach
    fun setUp() {
        searchService = SearchService(pageRepository)
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
}
