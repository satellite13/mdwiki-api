package com.mdwiki.service.usecase

import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.PatchPageRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
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
class PatchPageUseCaseTest {
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var updatePageUseCase: UpdatePageUseCase

    private val frontmatterMetaService = FrontmatterMetaService()

    @Test
    fun `replaces unique fragment and delegates to update`() {
        val page = page("alpha\nbeta\ngamma")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("deploy")).thenReturn(page)
        val saved = pageResponse(page, "alpha\nBETA\ngamma")
        whenever(updatePageUseCase.execute(eq("deploy"), any(), eq("editor"))).thenReturn(saved)

        val result = useCase().execute(
            "deploy",
            PatchPageRequest(
                oldText = "beta",
                newText = "BETA",
                expectedUpdatedAt = page.updatedAt
            ),
            "editor"
        )

        assertEquals(1, result.replacements)
        assertEquals("alpha\nBETA\ngamma", result.contentMd)
        assertEquals(page.updatedAt, result.previousUpdatedAt)
        verify(updatePageUseCase).execute(
            eq("deploy"),
            eq(
                UpdatePageRequest(
                    contentMd = "alpha\nBETA\ngamma",
                    expectedUpdatedAt = page.updatedAt
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `rejects stale expectedUpdatedAt before writing`() {
        val page = page("beta")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("deploy")).thenReturn(page)

        assertThrows<ConflictException> {
            useCase().execute(
                "deploy",
                PatchPageRequest(
                    oldText = "beta",
                    newText = "BETA",
                    expectedUpdatedAt = page.updatedAt.minusSeconds(5)
                ),
                "editor"
            )
        }
    }

    @Test
    fun `rejects locked page`() {
        val page = page("---\nlocked: true\n---\nbeta")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("deploy")).thenReturn(page)

        assertThrows<ForbiddenException> {
            useCase().execute(
                "deploy",
                PatchPageRequest(oldText = "beta", newText = "BETA", expectedUpdatedAt = page.updatedAt),
                "editor"
            )
        }
    }

    @Test
    fun `scopes oldText search to sectionKey`() {
        val page = page("# A\nfoo\n\n# B\nfoo")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("deploy")).thenReturn(page)
        whenever(updatePageUseCase.execute(eq("deploy"), any(), eq("editor"))).thenAnswer {
            val req = it.arguments[1] as UpdatePageRequest
            pageResponse(page, req.contentMd!!)
        }

        useCase().execute(
            "deploy",
            PatchPageRequest(
                oldText = "foo",
                newText = "bar",
                expectedUpdatedAt = page.updatedAt,
                sectionKey = "b"
            ),
            "editor"
        )

        verify(updatePageUseCase).execute(
            eq("deploy"),
            eq(UpdatePageRequest(contentMd = "# A\nfoo\n\n# B\nbar", expectedUpdatedAt = page.updatedAt)),
            eq("editor")
        )
    }

    @Test
    fun `rejects oversized newText`() {
        assertThrows<IllegalArgumentException> {
            useCase().execute(
                "deploy",
                PatchPageRequest(
                    oldText = "beta",
                    newText = "x".repeat(PatchPageUseCase.MAX_NEW_TEXT_CHARS + 1),
                    expectedUpdatedAt = Instant.parse("2026-08-15T10:00:00Z")
                ),
                "editor"
            )
        }
    }

    private fun useCase() = PatchPageUseCase(
        pageRepository,
        frontmatterMetaService,
        updatePageUseCase
    )

    private fun page(content: String) = Page(
        id = UUID.randomUUID(),
        slug = "deploy",
        title = "Deploy",
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
