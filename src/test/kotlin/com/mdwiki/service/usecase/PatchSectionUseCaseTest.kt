package com.mdwiki.service.usecase

import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.PatchSectionMode
import com.mdwiki.dto.PatchSectionRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.FrontmatterMetaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PatchSectionUseCaseTest {
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var updatePageUseCase: UpdatePageUseCase

    private val frontmatterMetaService = FrontmatterMetaService()

    @Test
    fun `replaces section body and keeps heading`() {
        val md = "# Intro\nbefore\n\n## API\nold\n\n## Other\nkeep"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(updatePageUseCase.execute(eq("note"), any(), eq("editor"))).thenAnswer {
            val req = it.arguments[1] as UpdatePageRequest
            pageResponse(page, req.contentMd!!)
        }

        val result = useCase().execute(
            "note",
            PatchSectionRequest(
                sectionKey = "intro/api",
                content = "new\n",
                expectedUpdatedAt = page.updatedAt,
                mode = PatchSectionMode.BODY
            ),
            "editor"
        )

        assertEquals(1, result.replacements)
        assertEquals("intro/api", result.sectionKey)
        verify(updatePageUseCase).execute(
            eq("note"),
            eq(
                UpdatePageRequest(
                    contentMd = "# Intro\nbefore\n\n## API\nnew\n## Other\nkeep",
                    expectedUpdatedAt = page.updatedAt
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `rejects missing key stale hash and updatedAt`() {
        val md = "## API\nbody\n"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        val useCase = useCase()

        assertThrows<NotFoundException> {
            useCase.execute(
                "note",
                PatchSectionRequest(sectionKey = "missing", content = "x", expectedUpdatedAt = page.updatedAt),
                "editor"
            )
        }
        assertThrows<ConflictException> {
            useCase.execute(
                "note",
                PatchSectionRequest(
                    sectionKey = "api",
                    content = "x",
                    expectedUpdatedAt = page.updatedAt.minusSeconds(1)
                ),
                "editor"
            )
        }
        assertThrows<ConflictException> {
            useCase.execute(
                "note",
                PatchSectionRequest(
                    sectionKey = "api",
                    content = "x",
                    expectedUpdatedAt = page.updatedAt,
                    expectedHash = "deadbeef"
                ),
                "editor"
            )
        }
    }

    @Test
    fun `replaces heading and body in section mode`() {
        val md = "# Intro\nbefore\n\n## API\nold\n"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(updatePageUseCase.execute(eq("note"), any(), eq("editor"))).thenAnswer {
            val req = it.arguments[1] as UpdatePageRequest
            pageResponse(page, req.contentMd!!)
        }

        useCase().execute(
            "note",
            PatchSectionRequest(
                sectionKey = "intro/api",
                content = "## HTTP\nnew\n",
                expectedUpdatedAt = page.updatedAt,
                mode = PatchSectionMode.SECTION
            ),
            "editor"
        )

        verify(updatePageUseCase).execute(
            eq("note"),
            eq(
                UpdatePageRequest(
                    contentMd = "# Intro\nbefore\n\n## HTTP\nnew\n",
                    expectedUpdatedAt = page.updatedAt
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `inserts newline so following heading is not glued`() {
        val md = "# Intro\nbefore\n\n## API\nold\n\n## Other\nkeep"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(updatePageUseCase.execute(eq("note"), any(), eq("editor"))).thenAnswer {
            val req = it.arguments[1] as UpdatePageRequest
            pageResponse(page, req.contentMd!!)
        }

        useCase().execute(
            "note",
            PatchSectionRequest(
                sectionKey = "intro/api",
                content = "new",
                expectedUpdatedAt = page.updatedAt,
                mode = PatchSectionMode.BODY
            ),
            "editor"
        )

        verify(updatePageUseCase).execute(
            eq("note"),
            eq(
                UpdatePageRequest(
                    contentMd = "# Intro\nbefore\n\n## API\nnew\n## Other\nkeep",
                    expectedUpdatedAt = page.updatedAt
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `keeps last section without trailing newline`() {
        val md = "## API\nold"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)
        whenever(updatePageUseCase.execute(eq("note"), any(), eq("editor"))).thenAnswer {
            val req = it.arguments[1] as UpdatePageRequest
            pageResponse(page, req.contentMd!!)
        }

        useCase().execute(
            "note",
            PatchSectionRequest(
                sectionKey = "api",
                content = "new",
                expectedUpdatedAt = page.updatedAt,
                mode = PatchSectionMode.BODY
            ),
            "editor"
        )

        verify(updatePageUseCase).execute(
            eq("note"),
            eq(
                UpdatePageRequest(
                    contentMd = "## API\nnew",
                    expectedUpdatedAt = page.updatedAt
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `rejects patch that swallows a following heading`() {
        val md = "## API\nold\n\n## Other\nkeep"
        val page = page(md)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("note")).thenReturn(page)

        val error = assertThrows<IllegalArgumentException> {
            useCase().execute(
                "note",
                PatchSectionRequest(
                    sectionKey = "api",
                    content = "```\nunterminated",
                    expectedUpdatedAt = page.updatedAt,
                    mode = PatchSectionMode.BODY
                ),
                "editor"
            )
        }

        assertEquals(
            "Patch would swallow following section(s): other. End content with a newline and close fences so the next heading stays on its own line.",
            error.message
        )
    }

    private fun useCase() = PatchSectionUseCase(pageRepository, frontmatterMetaService, updatePageUseCase)

    private fun page(content: String) = Page(
        id = UUID.randomUUID(),
        slug = "note",
        title = "Note",
        contentMd = content,
        updatedAt = Instant.parse("2026-08-15T10:00:00Z")
    )

    private fun pageResponse(page: Page, content: String) = PageResponse(
        id = page.id!!,
        slug = page.slug,
        title = page.title,
        contentMd = content,
        tags = emptyList(),
        createdBy = null,
        updatedBy = "editor",
        createdAt = Instant.parse("2026-08-15T09:00:00Z"),
        updatedAt = Instant.parse("2026-08-15T11:00:00Z")
    )
}
