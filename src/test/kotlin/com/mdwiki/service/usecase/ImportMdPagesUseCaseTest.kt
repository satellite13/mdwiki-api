package com.mdwiki.service.usecase

import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.ImportMdFileInput
import com.mdwiki.dto.ImportMdItemStatus
import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.MultiPageMutationLock
import com.mdwiki.service.WikilinkService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ImportMdPagesUseCaseTest {

    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var createPageUseCase: CreatePageUseCase
    @Mock private lateinit var updatePageUseCase: UpdatePageUseCase

    private val wikilinkService = WikilinkService()

    private fun useCase() = ImportMdPagesUseCase(
        pageRepository, createPageUseCase, updatePageUseCase, wikilinkService
    )

    private fun pageResponse(slug: String, title: String) = PageResponse(
        id = UUID.randomUUID(),
        slug = slug,
        title = title,
        contentMd = "body",
        tags = emptyList(),
        createdBy = "editor",
        updatedBy = "editor",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `creates page when slug is free`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("my-note")).thenReturn(null)
        whenever(pageRepository.findBySlug("my-note")).thenReturn(null)
        whenever(createPageUseCase.execute(any(), eq("editor"))).thenReturn(pageResponse("my-note", "My Note"))

        val result = useCase().execute(
            listOf(ImportMdFileInput("My Note.md", "# My Note\nhello")),
            folderId = null,
            overwrite = false,
            username = "editor"
        )

        assertEquals(1, result.created)
        assertEquals(ImportMdItemStatus.CREATED, result.results[0].status)
        assertEquals("my-note", result.results[0].slug)
        verify(createPageUseCase).execute(
            eq(CreatePageRequest(slug = "my-note", title = "My Note", contentMd = "# My Note\nhello", folderId = null)),
            eq("editor")
        )
        inOrder(pageRepository, createPageUseCase) {
            verify(pageRepository).acquireTransactionAdvisoryLock(MultiPageMutationLock.KEY)
            verify(pageRepository).findBySlugAndDeletedAtIsNull("my-note")
            verify(createPageUseCase).execute(any(), eq("editor"))
        }
    }

    @Test
    fun `skips existing page when overwrite is false`() {
        val existing = Page(id = UUID.randomUUID(), slug = "my-note", title = "Old", contentMd = "old")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("my-note")).thenReturn(existing)

        val result = useCase().execute(
            listOf(ImportMdFileInput("my-note.md", "new")),
            folderId = null,
            overwrite = false,
            username = "editor"
        )

        assertEquals(1, result.skipped)
        assertEquals(ImportMdItemStatus.SKIPPED, result.results[0].status)
        verify(createPageUseCase, never()).execute(any(), any())
        verify(updatePageUseCase, never()).execute(any(), any(), any())
    }

    @Test
    fun `overwrites existing page when overwrite is true`() {
        val existing = Page(id = UUID.randomUUID(), slug = "my-note", title = "Old", contentMd = "old")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("my-note")).thenReturn(existing)
        whenever(updatePageUseCase.execute(eq("my-note"), any(), eq("editor")))
            .thenReturn(pageResponse("my-note", "New Title"))

        val folderId = UUID.randomUUID()
        val result = useCase().execute(
            listOf(ImportMdFileInput("my-note.md", "---\ntitle: New Title\n---\nbody")),
            folderId = folderId,
            overwrite = true,
            username = "editor"
        )

        assertEquals(1, result.updated)
        assertEquals(ImportMdItemStatus.UPDATED, result.results[0].status)
        verify(updatePageUseCase).execute(
            eq("my-note"),
            eq(
                UpdatePageRequest(
                    title = "New Title",
                    contentMd = "---\ntitle: New Title\n---\nbody",
                    folderId = folderId,
                    clearFolder = null
                )
            ),
            eq("editor")
        )
    }

    @Test
    fun `locked page returns error even with overwrite`() {
        val existing = Page(id = UUID.randomUUID(), slug = "locked", title = "Locked", contentMd = "x")
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("locked")).thenReturn(existing)
        whenever(updatePageUseCase.execute(eq("locked"), any(), eq("editor")))
            .thenThrow(ForbiddenException("Page 'locked' is locked and cannot be edited"))

        val result = useCase().execute(
            listOf(ImportMdFileInput("locked.md", "new")),
            folderId = null,
            overwrite = true,
            username = "editor"
        )

        assertEquals(1, result.errors)
        assertEquals(ImportMdItemStatus.ERROR, result.results[0].status)
        assertEquals("Page 'locked' is locked and cannot be edited", result.results[0].message)
    }

    @Test
    fun `soft-deleted slug returns error without restore`() {
        val deleted = Page(
            id = UUID.randomUUID(),
            slug = "gone",
            title = "Gone",
            contentMd = "x",
            deletedAt = Instant.now()
        )
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("gone")).thenReturn(null)
        whenever(pageRepository.findBySlug("gone")).thenReturn(deleted)

        val result = useCase().execute(
            listOf(ImportMdFileInput("gone.md", "new")),
            folderId = null,
            overwrite = true,
            username = "editor"
        )

        assertEquals(1, result.errors)
        assertEquals(ImportMdItemStatus.ERROR, result.results[0].status)
        verify(createPageUseCase, never()).execute(any(), any())
        verify(updatePageUseCase, never()).execute(any(), any(), any())
    }

    @Test
    fun `invalid extension and blank slug are errors`() {
        val result = useCase().execute(
            listOf(
                ImportMdFileInput("note.txt", "x"),
                ImportMdFileInput("---.md", "x")
            ),
            folderId = null,
            overwrite = false,
            username = "editor"
        )

        assertEquals(2, result.errors)
        assertNull(result.results[0].slug)
        assertEquals("Cannot derive a valid slug from filename", result.results[1].message)
    }
}
