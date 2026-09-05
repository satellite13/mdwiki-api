package com.mdwiki.service

import com.mdwiki.dto.LinkUnlinkedMentionRequest
import com.mdwiki.dto.OrphanDefinition
import com.mdwiki.dto.UrlCaptureRequest
import com.mdwiki.dto.PageResponse
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.error.BadRequestException
import com.mdwiki.error.ConflictException
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.model.Folder
import com.mdwiki.model.UserPkmSettings
import com.mdwiki.repository.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PkmServiceTest {
    @Mock lateinit var users: UserRepository
    @Mock lateinit var pages: PageRepository
    @Mock lateinit var settings: UserPkmSettingsRepository
    @Mock lateinit var daily: UserDailyNoteRepository
    @Mock lateinit var recent: UserRecentPageRepository
    @Mock lateinit var favorites: UserFavoritePageRepository
    @Mock lateinit var links: LinkRepository
    @Mock lateinit var sections: PageSectionRepository
    @Mock lateinit var folderService: FolderService
    @Mock lateinit var pageService: PageService
    @Mock lateinit var attachments: AttachmentService
    private lateinit var service: PkmService

    @BeforeEach
    fun setUp() {
        service = PkmService(users, pages, settings, daily, recent, favorites, links, sections,
            folderService, pageService, attachments)
    }

    @Test
    fun `invalid mention range is bad request before optimistic conflict`() {
        val target = page("target", "Target", "")
        val source = page("source", "Source", "Target")
        whenever(pages.findBySlugAndDeletedAtIsNull("target")).thenReturn(target)
        whenever(pages.findActiveBySlugForUpdate("source")).thenReturn(source)

        listOf(-1 to 1, 5 to 2, 0 to 7).forEach { (start, end) ->
            assertThrows<BadRequestException> {
                service.linkMention("target", LinkUnlinkedMentionRequest(
                    "source", start, end, Instant.EPOCH
                ), "alice")
            }
        }
    }

    @Test
    fun `valid mention range reports optimistic conflict for stale timestamp`() {
        val target = page("target", "Target", "")
        val source = page("source", "Source", "Target")
        whenever(pages.findBySlugAndDeletedAtIsNull("target")).thenReturn(target)
        whenever(pages.findActiveBySlugForUpdate("source")).thenReturn(source)

        assertThrows<ConflictException> {
            service.linkMention("target", LinkUnlinkedMentionRequest(
                "source", 0, 6, Instant.EPOCH
            ), "alice")
        }
    }

    @Test
    fun `URL capture rejects non HTTP absolute URL before creating page`() {
        assertThrows<IllegalArgumentException> {
            service.captureUrl(UrlCaptureRequest("file:///etc/passwd"), "alice")
        }
        org.mockito.kotlin.verifyNoInteractions(pageService)
    }

    @Test
    fun `image capture compensates attachment and page after later update failure`() {
        val userId = UUID.randomUUID()
        val owner = User(userId, "alice", "alice@test", "x", UserRole.EDITOR)
        val inbox = Folder(UUID.randomUUID(), "Inbox", createdBy = owner, owner = owner)
        whenever(users.findByUsername("alice")).thenReturn(owner)
        whenever(settings.findById(userId)).thenReturn(Optional.of(UserPkmSettings(userId, owner, inboxFolder = inbox)))
        val created = PageResponse(UUID.randomUUID(), "capture-x", "Image", "", tags = emptyList(),
            createdBy = "alice", updatedBy = "alice", createdAt = Instant.now(), updatedAt = Instant.now())
        val attachment = AttachmentResponse(UUID.randomUUID(), "x.png", "stored.png", "image/png", 8L,
            "alice", created.id, "/api/uploads/stored.png", Instant.now())
        whenever(pageService.create(any(), eq("alice"))).thenReturn(created)
        whenever(attachments.upload(any(), eq("alice"), eq(created.id))).thenReturn(attachment)
        whenever(pageService.update(eq(created.slug), any(), eq("alice"))).thenThrow(IllegalStateException("update failed"))
        val png = MockMultipartFile("file", "x.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

        assertThrows<IllegalStateException> { service.captureImage(png, null, null, "alice") }

        verify(attachments).delete(attachment.id)
        verify(pageService).delete(eq(created.slug), any())
    }

    @Test
    fun `image capture rejects oversized title before mutation`() {
        val png = MockMultipartFile("file", "x.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        assertThrows<IllegalArgumentException> {
            service.captureImage(png, null, "x".repeat(501), "alice")
        }
        org.mockito.kotlin.verifyNoInteractions(pageService)
    }

    @Test
    fun `orphan definitions ignore deleted resolved target and count unresolved outgoing`() {
        val a = page("a", "A", "")
        val b = page("b", "B", "")
        val deleted = page("deleted", "Deleted", "").also { it.deletedAt = Instant.now() }
        whenever(pages.findAllByDeletedAtIsNull()).thenReturn(listOf(a, b))
        whenever(links.findAllWithPages()).thenReturn(listOf(
            Link(sourcePage = a, targetPage = null, targetSlug = "missing"),
            Link(sourcePage = a, targetPage = deleted, targetSlug = "deleted")
        ))

        assertThat(service.orphans(OrphanDefinition.NO_INCOMING).map { it.page.slug })
            .containsExactly("a", "b")
        assertThat(service.orphans(OrphanDefinition.NO_OUTGOING).map { it.page.slug })
            .containsExactly("b")
        assertThat(service.orphans(OrphanDefinition.NO_LINKS).map { it.page.slug })
            .containsExactly("b")
        assertThat(service.orphans(OrphanDefinition.NO_INCOMING).first { it.page.slug == "a" }.outgoingCount)
            .isEqualTo(1)
    }

    private fun page(slug: String, title: String, content: String) = Page(
        id = UUID.randomUUID(), slug = slug, title = title, contentMd = content,
        createdAt = Instant.parse("2026-09-05T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-05T10:00:00Z")
    )
}
