package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.UpdateAnnotationRequest
import com.mdwiki.dto.CreateAnnotationRequest
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Annotation
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.AnnotationRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AnnotationServiceTest {
    @Mock private lateinit var annotationRepository: AnnotationRepository
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var userRepository: UserRepository
    @TempDir lateinit var tempDir: Path

    @Test
    fun `explicit clear flags remove comment and color`() {
        val page = Page(id = UUID.randomUUID(), slug = "note", title = "Note")
        val annotation = Annotation(
            id = UUID.randomUUID(),
            pageId = page.id!!,
            highlightedText = "selected",
            anchorContext = "selected context",
            comment = "old comment",
            color = "#ffeb3b",
            createdBy = "editor"
        )
        whenever(annotationRepository.findById(annotation.id!!)).thenReturn(Optional.of(annotation))
        whenever(annotationRepository.save(any<Annotation>())).thenAnswer { it.arguments[0] }
        whenever(annotationRepository.findByPageId(page.id!!)).thenReturn(listOf(annotation))
        whenever(pageRepository.findById(page.id!!)).thenReturn(Optional.of(page))
        whenever(pageRepository.findBySlug(page.slug)).thenReturn(page)
        val service = service()

        val result = service.update(
            annotation.id!!,
            UpdateAnnotationRequest(clearComment = true, clearColor = true),
            "editor"
        )

        assertNull(result.comment)
        assertNull(result.color)
    }

    @Test
    fun `foreign editor cannot create annotation on owned page`() {
        val alice = User(UUID.randomUUID(), "alice", "a@test", "x", UserRole.EDITOR)
        val bob = User(UUID.randomUUID(), "bob", "b@test", "x", UserRole.EDITOR)
        val page = Page(UUID.randomUUID(), "owned", "Owned", folder = Folder(UUID.randomUUID(), "Inbox", owner = alice))
        whenever(pageRepository.findBySlug("owned")).thenReturn(page)
        whenever(userRepository.findByUsername("bob")).thenReturn(bob)

        assertThrows<ForbiddenException> {
            service().create("owned", CreateAnnotationRequest("text", "context"), "bob")
        }
    }

    private fun service() = AnnotationService(
        annotationRepository,
        pageRepository,
        WikiProperties(contentDir = tempDir.toString()),
        FolderAccessPolicy(userRepository)
    )
}
