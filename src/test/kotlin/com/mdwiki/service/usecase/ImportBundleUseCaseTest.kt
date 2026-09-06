package com.mdwiki.service.usecase

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mdwiki.config.WikiProperties
import com.mdwiki.dto.AttachmentResponse
import com.mdwiki.dto.BundleManifest
import com.mdwiki.dto.BundleManifestAttachment
import com.mdwiki.dto.BundleManifestFolder
import com.mdwiki.dto.BundleManifestPage
import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.FolderResponse
import com.mdwiki.dto.PageResponse
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.AttachmentService
import com.mdwiki.service.FolderService
import com.mdwiki.service.MultiPageMutationLock
import com.mdwiki.service.WikilinkService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExtendWith(MockitoExtension::class)
class ImportBundleUseCaseTest {

    @Mock private lateinit var folderService: FolderService
    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var createPageUseCase: CreatePageUseCase
    @Mock private lateinit var attachmentService: AttachmentService

    private val mapper = jacksonObjectMapper()
    private val wikilinks = WikilinkService()

    private lateinit var useCase: ImportBundleUseCase

    @BeforeEach
    fun setUp() {
        useCase = ImportBundleUseCase(
            mapper,
            folderService,
            folderRepository,
            pageRepository,
            createPageUseCase,
            attachmentService,
            wikilinks,
            WikiProperties()
        )
    }

    @Test
    fun `safeResolve rejects zip-slip paths`() {
        val dest = java.nio.file.Files.createTempDirectory("bundle-slip")
        assertThrows(IllegalArgumentException::class.java) {
            ImportBundleUseCase.safeResolve(dest, "../../etc/passwd")
        }
        dest.toFile().deleteRecursively()
    }

    @Test
    fun `imports as new subtree remapping colliding slugs and upload urls`() {
        val targetId = UUID.randomUUID()
        val createdFolderId = UUID.randomUUID()
        whenever(folderRepository.findById(targetId)).thenReturn(Optional.of(com.mdwiki.model.Folder(id = targetId, name = "Inbox")))
        whenever(folderRepository.existsByParentIdAndName(targetId, "Book")).thenReturn(true)
        whenever(folderRepository.existsByParentIdAndName(targetId, "Book (2)")).thenReturn(false)
        whenever(folderService.create(any(), eq("editor"))).thenReturn(
            FolderResponse(createdFolderId, "Book (2)", targetId, 0, Instant.now())
        )
        whenever(pageRepository.existsBySlug("intro")).thenReturn(true)
        whenever(pageRepository.existsBySlug("intro-2")).thenReturn(false)
        whenever(pageRepository.existsBySlug("chapter-1")).thenReturn(false)
        whenever(attachmentService.uploadFromTrustedPath(any(), eq("editor"), eq(null), eq("diagram.png"), eq("image/png")))
            .thenReturn(
                AttachmentResponse(
                    id = UUID.randomUUID(),
                    originalName = "diagram.png",
                    storedName = "new-pic.png",
                    contentType = "image/png",
                    sizeBytes = 3,
                    uploadedBy = "editor",
                    pageId = null,
                    url = "/api/uploads/new-pic.png",
                    createdAt = Instant.now()
                )
            )
        whenever(createPageUseCase.execute(any(), eq("editor"))).thenAnswer { inv ->
            val req = inv.getArgument<CreatePageRequest>(0)
            PageResponse(
                id = UUID.randomUUID(),
                slug = req.slug,
                title = req.title,
                contentMd = req.contentMd,
                tags = emptyList(),
                createdBy = "editor",
                updatedBy = "editor",
                folderId = req.folderId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        val zip = bundleZip(
            pages = listOf(
                Triple(
                    "intro",
                    "Intro",
                    "see [[chapter-1]] and [ch](/page/chapter-1) ![](/api/uploads/pic.png) and [[outside]]"
                ),
                Triple("chapter-1", "Chapter 1", "body")
            ),
            folderPath = listOf("Book"),
            attachmentBytes = byteArrayOf(1, 2, 3)
        )

        val result = useCase.execute(ByteArrayInputStream(zip), targetId, "editor", zip.size.toLong())

        inOrder(pageRepository, folderRepository) {
            verify(pageRepository).acquireTransactionAdvisoryLock(MultiPageMutationLock.KEY)
            verify(folderRepository).findById(targetId)
        }
        assertEquals(2, result.createdPages)
        assertEquals(1, result.createdFolders)
        assertEquals(listOf(com.mdwiki.dto.BundleSlugRemap("intro", "intro-2")), result.remappedSlugs)
        assertEquals(1, result.attachments)

        val folderCaptor = argumentCaptor<CreateFolderRequest>()
        org.mockito.kotlin.verify(folderService).create(folderCaptor.capture(), eq("editor"))
        assertEquals("Book (2)", folderCaptor.firstValue.name)
        assertEquals(targetId, folderCaptor.firstValue.parentId)

        val pageCaptor = argumentCaptor<CreatePageRequest>()
        org.mockito.kotlin.verify(createPageUseCase, org.mockito.kotlin.times(2))
            .execute(pageCaptor.capture(), eq("editor"))
        val intro = pageCaptor.allValues.first { it.slug == "intro-2" }
        assertEquals(createdFolderId, intro.folderId)
        assertTrue(intro.contentMd.contains("[[chapter-1]]"))
        assertTrue(intro.contentMd.contains("[ch](/page/chapter-1)"))
        assertTrue(intro.contentMd.contains("/api/uploads/new-pic.png"))
        assertTrue(intro.contentMd.contains("[[outside]]"))
        assertTrue(!intro.contentMd.contains("/api/uploads/pic.png"))
    }

    @Test
    fun `does not rewrite slugs of pages outside the bundle`() {
        whenever(pageRepository.existsBySlug("only")).thenReturn(false)
        whenever(createPageUseCase.execute(any(), eq("editor"))).thenAnswer { inv ->
            val req = inv.getArgument<CreatePageRequest>(0)
            PageResponse(
                id = UUID.randomUUID(),
                slug = req.slug,
                title = req.title,
                contentMd = req.contentMd,
                tags = emptyList(),
                createdBy = "editor",
                updatedBy = "editor",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        val zip = bundleZip(
            pages = listOf(Triple("only", "Only", "see [[missing-page]]")),
            folderPath = emptyList()
        )
        useCase.execute(ByteArrayInputStream(zip), null, "editor")

        val pageCaptor = argumentCaptor<CreatePageRequest>()
        org.mockito.kotlin.verify(createPageUseCase).execute(pageCaptor.capture(), eq("editor"))
        assertEquals("see [[missing-page]]", pageCaptor.firstValue.contentMd)
    }

    private fun bundleZip(
        pages: List<Triple<String, String, String>>,
        folderPath: List<String>,
        attachmentBytes: ByteArray? = null
    ): ByteArray {
        val folders = if (folderPath.isEmpty()) emptyList()
        else listOf(BundleManifestFolder(folderPath, folderPath.last()))
        val manifest = BundleManifest(
            folders = folders,
            pages = pages.map { (slug, title, _) ->
                BundleManifestPage(
                    slug = slug,
                    title = title,
                    folderPath = folderPath,
                    file = "pages/$slug.md",
                    attachmentRefs = if (attachmentBytes != null) listOf("pic.png") else emptyList()
                )
            },
            attachments = if (attachmentBytes != null) {
                listOf(
                    BundleManifestAttachment(
                        storedName = "pic.png",
                        originalName = "diagram.png",
                        contentType = "image/png",
                        file = "attachments/pic.png",
                        referencedBy = listOf(pages.first().first)
                    )
                )
            } else emptyList()
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(mapper.writeValueAsBytes(manifest))
            zip.closeEntry()
            for ((slug, _, content) in pages) {
                zip.putNextEntry(ZipEntry("pages/$slug.md"))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            if (attachmentBytes != null) {
                zip.putNextEntry(ZipEntry("attachments/pic.png"))
                zip.write(attachmentBytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
