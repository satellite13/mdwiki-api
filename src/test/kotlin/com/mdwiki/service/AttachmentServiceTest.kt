package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Attachment
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doThrow
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Optional
import java.util.UUID
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@ExtendWith(MockitoExtension::class)
class AttachmentServiceTest {

    @Mock
    private lateinit var attachmentRepository: AttachmentRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var pageRepository: PageRepository

    @TempDir
    lateinit var contentRoot: Path

    private lateinit var service: AttachmentService

    private val uploader = User(username = "alice", email = "a@x", passwordHash = "x", role = UserRole.EDITOR)

    @BeforeEach
    fun setUp() {
        service = AttachmentService(
            attachmentRepository,
            userRepository,
            pageRepository,
            WikiProperties(contentDir = contentRoot.toString()),
            FolderAccessPolicy(userRepository)
        )
    }

    @Test
    fun `list without pageId uses findAll`() {
        whenever(attachmentRepository.findAll(any<Pageable>())).thenReturn(PageImpl(emptyList()))

        service.list(0, 50, null, null, "reader")

        verify(attachmentRepository).findAll(any<Pageable>())
    }

    @Test
    fun `list with pageId uses findByPageId`() {
        val pid = UUID.randomUUID()
        whenever(attachmentRepository.findByPageId(any<UUID>(), any<Pageable>())).thenReturn(PageImpl(emptyList()))

        service.list(0, 50, pid, null, "reader")

        verify(attachmentRepository).findByPageId(any<UUID>(), any<Pageable>())
    }

    @Test
    fun `list without q uses findAll`() {
        whenever(attachmentRepository.findAll(any<Pageable>())).thenReturn(PageImpl(emptyList()))
        val result = service.list(0, 20, null, null, "reader")
        assertEquals(0, result.totalElements)
        verify(attachmentRepository).findAll(any<Pageable>())
    }

    @Test
    fun `list with q uses name search`() {
        whenever(
            attachmentRepository.findByOriginalNameContainingIgnoreCase(eq("note"), any<Pageable>())
        ).thenReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))
        service.list(0, 20, null, "note", "reader")
        verify(attachmentRepository).findByOriginalNameContainingIgnoreCase(eq("note"), any<Pageable>())
    }

    @Test
    fun `list with pageId and q uses combined search`() {
        val pid = UUID.randomUUID()
        whenever(
            attachmentRepository.findByPageIdAndOriginalNameContainingIgnoreCase(eq(pid), eq("img"), any<Pageable>())
        ).thenReturn(PageImpl(emptyList()))
        service.list(0, 20, pid, "img", "reader")
        verify(attachmentRepository)
            .findByPageIdAndOriginalNameContainingIgnoreCase(eq(pid), eq("img"), any<Pageable>())
    }

    @Test
    fun `upload stores file under uploads and saves attachment`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(uploader)
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { inv ->
            val a = inv.getArgument<Attachment>(0)
            Attachment(
                id = UUID.randomUUID(),
                originalName = a.originalName,
                storedName = a.storedName,
                contentType = a.contentType,
                sizeBytes = a.sizeBytes,
                uploadedBy = a.uploadedBy,
                page = a.page
            )
        }

        val file = MockMultipartFile("file", "note.txt", "text/plain", "hi".toByteArray())
        val response = service.upload(file, "alice", null)

        val uploads = contentRoot.resolve("uploads")
        assertTrue(Files.exists(uploads.resolve(response.storedName)))
        verify(attachmentRepository).save(any<Attachment>())
        assertEquals("/api/uploads/${response.storedName}", response.url)
    }

    @Test
    fun `foreign editor cannot upload attachment to owned page`() {
        val alice = User(UUID.randomUUID(), "alice-owner", "owner@test", "x", UserRole.EDITOR)
        val bob = User(UUID.randomUUID(), "bob", "bob@test", "x", UserRole.EDITOR)
        val page = Page(
            UUID.randomUUID(), "owned", "Owned",
            folder = com.mdwiki.model.Folder(UUID.randomUUID(), "Inbox", owner = alice)
        )
        whenever(userRepository.findByUsername("bob")).thenReturn(bob)
        whenever(pageRepository.findById(page.id!!)).thenReturn(Optional.of(page))

        assertThrows<com.mdwiki.error.ForbiddenException> {
            service.upload(
                MockMultipartFile("file", "a.png", "image/png", byteArrayOf(1)),
                "bob",
                page.id
            )
        }
    }

    @Test
    fun `upload removes file when repository flush fails`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(uploader)
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { invocation ->
            val value = invocation.getArgument<Attachment>(0)
            Attachment(
                id = UUID.randomUUID(), originalName = value.originalName, storedName = value.storedName,
                contentType = value.contentType, sizeBytes = value.sizeBytes, uploadedBy = value.uploadedBy
            )
        }
        doThrow(IllegalStateException("db failed"))
            .whenever(attachmentRepository).flush()
        val file = MockMultipartFile("file", "note.txt", "text/plain", "hi".toByteArray())

        assertThrows<IllegalStateException> { service.upload(file, "alice", null) }

        val uploads = contentRoot.resolve("uploads")
        assertTrue(!Files.exists(uploads) || Files.list(uploads).use { it.findAny().isEmpty })
    }

    @Test
    fun `upload removes returned file when transaction rolls back`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(uploader)
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { invocation ->
            val value = invocation.getArgument<Attachment>(0)
            Attachment(id = UUID.randomUUID(), originalName = value.originalName, storedName = value.storedName,
                contentType = value.contentType, sizeBytes = value.sizeBytes, uploadedBy = value.uploadedBy)
        }
        TransactionSynchronizationManager.initSynchronization()
        try {
            val response = service.upload(
                MockMultipartFile("file", "note.txt", "text/plain", "hi".toByteArray()),
                "alice", null
            )
            val stored = contentRoot.resolve("uploads").resolve(response.storedName)
            assertTrue(Files.exists(stored))
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }
            assertTrue(Files.notExists(stored))
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `upload rejects files larger than 20MB`() {
        val huge = MockMultipartFile(
            "file",
            "huge.bin",
            "application/octet-stream",
            ByteArray(1)
        )
        val oversized = object : MockMultipartFile("file", "huge.bin", "application/octet-stream", ByteArray(1)) {
            override fun getSize(): Long = AttachmentService.MAX_ATTACHMENT_BYTES + 1
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.upload(oversized, "alice", null)
        }
        assertTrue(huge.size < AttachmentService.MAX_ATTACHMENT_BYTES)
    }

    @Test
    fun `upload links attachment to page when pageId provided`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(uploader)
        val pageId = UUID.randomUUID()
        val page = Page(id = pageId, slug = "home", title = "Home", contentMd = "content")
        whenever(pageRepository.findById(pageId)).thenReturn(Optional.of(page))
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { inv ->
            val a = inv.getArgument<Attachment>(0)
            Attachment(
                id = UUID.randomUUID(),
                originalName = a.originalName,
                storedName = a.storedName,
                contentType = a.contentType,
                sizeBytes = a.sizeBytes,
                uploadedBy = a.uploadedBy,
                page = a.page
            )
        }

        val file = MockMultipartFile("file", "page-note.txt", "text/plain", "hello".toByteArray())
        val response = service.upload(file, "alice", pageId)

        assertEquals(pageId, response.pageId)
    }

    @Test
    fun `uploadFromBase64 stores decoded file and returns url`() {
        whenever(userRepository.findByUsername("alice")).thenReturn(uploader)
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { inv ->
            val a = inv.getArgument<Attachment>(0)
            Attachment(
                id = UUID.randomUUID(),
                originalName = a.originalName,
                storedName = a.storedName,
                contentType = a.contentType,
                sizeBytes = a.sizeBytes,
                uploadedBy = a.uploadedBy,
                page = a.page
            )
        }

        val base64 = Base64.getEncoder().encodeToString("hi".toByteArray())
        val response = service.uploadFromBase64(
            base64Data = base64,
            filename = "note.txt",
            username = "alice",
            pageId = null,
            contentType = "text/plain"
        )

        val stored = contentRoot.resolve("uploads").resolve(response.storedName)
        assertTrue(Files.exists(stored))
        assertEquals("/api/uploads/${response.storedName}", response.url)
    }

    @Test
    fun `delete removes file and repository row`() {
        val id = UUID.randomUUID()
        val stored = "f1.bin"
        Files.createDirectories(contentRoot.resolve("uploads"))
        val path = contentRoot.resolve("uploads").resolve(stored)
        Files.writeString(path, "x")

        val att = Attachment(
            id = id,
            originalName = "f1.bin",
            storedName = stored,
            contentType = "application/octet-stream",
            sizeBytes = 1,
            uploadedBy = null,
            page = null
        )
        whenever(attachmentRepository.findById(id)).thenReturn(Optional.of(att))

        service.delete(id, "alice")

        assertTrue(!Files.exists(path))
        verify(attachmentRepository).delete(att)
    }

    @Test
    fun `deleteAllForPage removes linked attachments and files`() {
        val pageId = UUID.randomUUID()
        val stored = "page-pic.bin"
        Files.createDirectories(contentRoot.resolve("uploads"))
        val path = contentRoot.resolve("uploads").resolve(stored)
        Files.writeString(path, "x")
        val att = Attachment(
            id = UUID.randomUUID(),
            originalName = "pic.bin",
            storedName = stored,
            contentType = "application/octet-stream",
            sizeBytes = 1,
            uploadedBy = null,
            page = Page(id = pageId, slug = "with-pic", title = "With pic")
        )
        whenever(attachmentRepository.findByPageIdIn(listOf(pageId))).thenReturn(listOf(att))

        service.deleteAllForPage(pageId)

        assertTrue(!Files.exists(path))
        verify(attachmentRepository).delete(att)
    }

    @Test
    fun `delete throws when attachment missing`() {
        val id = UUID.randomUUID()
        whenever(attachmentRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.delete(id, "alice")
        }
    }

    @Test
    fun `syncFromDisk registers files missing in database`() {
        val pageId = UUID.randomUUID()
        val storedName = "abc-123.png"
        val uploads = contentRoot.resolve("uploads")
        Files.createDirectories(uploads)
        Files.write(uploads.resolve(storedName), byteArrayOf(1, 2, 3))

        whenever(attachmentRepository.findAll()).thenReturn(emptyList())
        whenever(attachmentRepository.save(any<Attachment>())).thenAnswer { it.arguments[0] }
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(
            listOf(
                Page(
                    id = pageId,
                    slug = "with-image",
                    title = "With image",
                    contentMd = "![pic](/api/uploads/$storedName)"
                )
            )
        )
        whenever(pageRepository.findById(pageId)).thenReturn(
            Optional.of(Page(id = pageId, slug = "with-image", title = "With image"))
        )

        val result = service.syncFromDisk()

        assertEquals(1, result.added)
        verify(attachmentRepository).save(argThat<Attachment> {
            this.storedName == storedName && page?.id == pageId
        })
    }
}
