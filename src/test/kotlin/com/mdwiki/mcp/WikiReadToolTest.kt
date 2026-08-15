package com.mdwiki.mcp

import com.mdwiki.dto.PageResponse
import com.mdwiki.service.PageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WikiReadToolTest {
    @Mock private lateinit var pageService: PageService

    @Test
    fun `returns full page when no slice options`() {
        whenever(pageService.findBySlug("note")).thenReturn(page("# Intro\nhello"))
        whenever(pageService.getBacklinks("note")).thenReturn(emptyList())

        val result = WikiReadTool(pageService).read("note", heading = null, maxChars = null, sectionKey = null)

        assertEquals("# Intro\nhello", result["contentMd"])
        assertEquals(false, result["contentTruncated"])
        assertEquals(13, result["contentLength"])
        assertEquals("2026-08-15T10:00:00Z", result["updatedAt"])
    }

    @Test
    fun `returns heading section and can truncate`() {
        val md = "# Intro\nbefore\n\n## API\nfirst\n\n## Other\nsecond"
        whenever(pageService.findBySlug("note")).thenReturn(page(md))
        whenever(pageService.getBacklinks("note")).thenReturn(emptyList())

        val section = WikiReadTool(pageService).read("note", heading = "API", maxChars = null, sectionKey = null)
        assertEquals("## API\nfirst", section["contentMd"])
        assertEquals("API", section["sectionHeading"])
        assertEquals(false, section["contentTruncated"])

        val truncated = WikiReadTool(pageService).read("note", heading = "API", maxChars = 5, sectionKey = null)
        assertEquals("## AP", truncated["contentMd"])
        assertEquals(true, truncated["contentTruncated"])
        assertEquals(12, truncated["contentLength"])
    }

    @Test
    fun `rejects unknown heading`() {
        whenever(pageService.findBySlug("note")).thenReturn(page("# Intro\nhello"))
        whenever(pageService.getBacklinks("note")).thenReturn(emptyList())

        assertThrows<IllegalArgumentException> {
            WikiReadTool(pageService).read("note", heading = "Missing", maxChars = null, sectionKey = null)
        }
    }

    @Test
    fun `reads by sectionKey over heading`() {
        val md = "# Intro\nbefore\n\n## API\nfirst\n\n## Other\nsecond"
        whenever(pageService.findBySlug("note")).thenReturn(page(md))
        whenever(pageService.getBacklinks("note")).thenReturn(emptyList())

        val result = WikiReadTool(pageService).read("note", heading = "Other", maxChars = null, sectionKey = "intro/api")
        assertEquals("intro/api", result["sectionKey"])
        assertEquals("Intro::API", result["headingPath"])
        assertTrue((result["contentMd"] as String).startsWith("## API"))
    }

    private fun page(content: String) = PageResponse(
        id = UUID.randomUUID(),
        slug = "note",
        title = "Note",
        contentMd = content,
        tags = emptyList(),
        createdBy = "u",
        updatedBy = "u",
        createdAt = Instant.parse("2026-08-15T09:00:00Z"),
        updatedAt = Instant.parse("2026-08-15T10:00:00Z")
    )
}
