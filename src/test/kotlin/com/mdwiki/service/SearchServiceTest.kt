package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
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
        val page = Page(id = UUID.randomUUID(), slug = "result-page", title = "Result", contentMd = "Some matching content here")
        whenever(pageRepository.fullTextSearch("matching", 20)).thenReturn(listOf(page))

        val results = searchService.search("matching")

        assertEquals(1, results.size)
        assertEquals("result-page", results[0].slug)
        assertEquals("Result", results[0].title)
    }

    @Test
    fun `search returns empty for no matches`() {
        whenever(pageRepository.fullTextSearch("nonexistent", 20)).thenReturn(emptyList())

        val results = searchService.search("nonexistent")

        assertTrue(results.isEmpty())
    }
}
