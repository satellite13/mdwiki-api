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
import org.mockito.kotlin.isNull
import org.mockito.kotlin.argThat
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
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
    fun `image capture deletes attachment when single page create fails`() {
        val userId = UUID.randomUUID()
        val owner = User(userId, "alice", "alice@test", "x", UserRole.EDITOR)
        val inbox = Folder(UUID.randomUUID(), "Inbox", createdBy = owner, owner = owner)
        whenever(users.findByUsername("alice")).thenReturn(owner)
        whenever(settings.findById(userId)).thenReturn(Optional.of(UserPkmSettings(userId, owner, inboxFolder = inbox)))
        val attachment = AttachmentResponse(UUID.randomUUID(), "x.png", "stored.png", "image/png", 8L,
            "alice", null, "/api/uploads/stored.png", Instant.now())
        whenever(attachments.upload(any(), eq("alice"), isNull())).thenReturn(attachment)
        whenever(pageService.create(any(), eq("alice"))).thenThrow(IllegalStateException("create failed"))
        val png = MockMultipartFile("file", "x.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

        assertThrows<IllegalStateException> { service.captureImage(png, null, null, "alice") }

        verify(attachments).deletePreAuthorized(attachment.id)
        verify(pageService, org.mockito.kotlin.never()).update(any(), any(), any())
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
    fun `image capture writes final markdown in one page create`() {
        val owner = User(UUID.randomUUID(), "alice", "alice@test", "x", UserRole.EDITOR)
        val inbox = Folder(UUID.randomUUID(), "Inbox", owner = owner)
        whenever(users.findByUsername("alice")).thenReturn(owner)
        whenever(settings.findById(owner.id!!)).thenReturn(Optional.of(UserPkmSettings(owner.id!!, owner, inboxFolder = inbox)))
        val attachment = AttachmentResponse(UUID.randomUUID(), "x.png", "stored.png", "image/png", 8L,
            "alice", null, "/api/uploads/stored.png", Instant.now())
        val page = PageResponse(
            id = UUID.randomUUID(), slug = "capture-x", title = "Image",
            contentMd = "![caption](/api/uploads/stored.png)", tags = emptyList(),
            createdBy = "alice", updatedBy = "alice", folderId = inbox.id,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        whenever(attachments.upload(any(), eq("alice"), isNull())).thenReturn(attachment)
        whenever(pageService.create(any(), eq("alice"))).thenReturn(page)
        whenever(attachments.linkPreAuthorized(attachment.id, page.id)).thenReturn(attachment.copy(pageId = page.id))
        val png = MockMultipartFile("file", "x.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

        service.captureImage(png, "caption", null, "alice")

        verify(pageService).create(argThat { contentMd == "![caption](/api/uploads/stored.png)" }, eq("alice"))
        verify(pageService, org.mockito.kotlin.never()).update(any(), any(), any())
    }

    @Test
    fun `image filename fallback is sanitized and bounded before page creation`() {
        val userId = UUID.randomUUID()
        val owner = User(userId, "alice", "alice@test", "x", UserRole.EDITOR)
        val inbox = Folder(UUID.randomUUID(), "Inbox", createdBy = owner, owner = owner)
        whenever(users.findByUsername("alice")).thenReturn(owner)
        whenever(settings.findById(userId)).thenReturn(Optional.of(UserPkmSettings(userId, owner, inboxFolder = inbox)))
        val attachment = AttachmentResponse(UUID.randomUUID(), "x.png", "stored.png", "image/png", 8L,
            "alice", null, "/api/uploads/stored.png", Instant.now())
        whenever(attachments.upload(any(), eq("alice"), isNull())).thenReturn(attachment)
        val capturedTitle = AtomicReference<String>()
        whenever(pageService.create(any(), eq("alice"))).thenAnswer { invocation ->
            capturedTitle.set(invocation.getArgument<com.mdwiki.dto.CreatePageRequest>(0).title)
            throw IllegalStateException("stop after create request")
        }
        val png = MockMultipartFile("file", "../${"a".repeat(600)}\u0000.png", "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

        assertThrows<IllegalStateException> { service.captureImage(png, null, null, "alice") }

        assertThat(capturedTitle.get()).hasSizeLessThanOrEqualTo(500)
            .doesNotContain("/", "\\", "\u0000")
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

    @Test
    fun `mention discovery batch loads sections once`() {
        val target = page("target", "Target", "")
        val first = page("first", "First", "Target one")
        val second = page("second", "Second", "Target two")
        whenever(pages.findBySlugAndDeletedAtIsNull("target")).thenReturn(target)
        whenever(pages.findAllByDeletedAtIsNull()).thenReturn(listOf(target, first, second))
        whenever(sections.findByPageIdInOrderByPageIdAscSortOrderAsc(any())).thenReturn(emptyList())

        service.mentions("target")

        verify(sections).findByPageIdInOrderByPageIdAscSortOrderAsc(any())
        verify(sections, org.mockito.kotlin.never()).findByPageIdOrderBySortOrder(any())
    }

    private fun page(slug: String, title: String, content: String) = Page(
        id = UUID.randomUUID(), slug = slug, title = title, contentMd = content,
        createdAt = Instant.parse("2026-09-05T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-05T10:00:00Z")
    )
}
