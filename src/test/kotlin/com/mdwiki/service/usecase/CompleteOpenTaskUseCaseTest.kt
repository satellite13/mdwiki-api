package com.mdwiki.service.usecase

import com.mdwiki.dto.CompleteOpenTaskRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Page
import com.mdwiki.model.Folder
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.DeferredPageIndexer
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.WikiFileService
import com.mdwiki.service.FolderAccessPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CompleteOpenTaskUseCaseTest {
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var pageMetadataService: PageMetadataService
    @Mock private lateinit var wikiFileService: WikiFileService
    @Mock private lateinit var pageIndexer: DeferredPageIndexer
    @Mock private lateinit var sectionIndexService: com.mdwiki.service.SectionIndexService

    private val frontmatterMetaService = FrontmatterMetaService()

    private fun useCase() = CompleteOpenTaskUseCase(
        pageRepository,
        userRepository,
        frontmatterMetaService,
        wikiFileService,
        pageMetadataService,
        pageIndexer,
        sectionIndexService,
        FolderAccessPolicy(userRepository)
    )

    @Test
    fun `completes task and preserves page update pipeline`() {
        val page = page(content = "- [ ] Deploy\nnext")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "editor@example.com", passwordHash = "hash")
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername(user.username)).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        useCase().execute(requestFor(page), user.username)

        assertEquals("- [x] Deploy\nnext", page.contentMd)
        assertEquals(user, page.updatedBy)
        verify(wikiFileService).createOrRewritePageFile(page, "- [x] Deploy\nnext")
        verify(pageRepository).save(argThat<Page> { contentMd == "- [x] Deploy\nnext" })
        verify(pageMetadataService).syncLinksAndTags(page, "- [x] Deploy\nnext", cleanupOrphanedTags = true)
        verify(pageIndexer).indexAfterCommit(page)
    }

    @Test
    fun `foreign editor cannot complete task on owned page`() {
        val alice = User(UUID.randomUUID(), "alice", "alice@test", "x", UserRole.EDITOR)
        val bob = User(UUID.randomUUID(), "bob", "bob@test", "x", UserRole.EDITOR)
        val page = page(content = "- [ ] Deploy").apply {
            folder = Folder(UUID.randomUUID(), "Inbox", owner = alice)
        }
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername("bob")).thenReturn(bob)

        assertThrows<ForbiddenException> { useCase().execute(requestFor(page), "bob") }
    }

    @Test
    fun `adds quote for every summary line immediately after task`() {
        val page = page(content = "- [ ] Deploy\nnext")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "editor@example.com", passwordHash = "hash")
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername(user.username)).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        useCase().execute(requestFor(page, "released\nverified"), user.username)

        assertEquals("- [x] Deploy\n> released\n> verified\nnext", page.contentMd)
    }

    @Test
    fun `adds quote for one line summary immediately after task`() {
        val page = page(content = "- [ ] Deploy")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "editor@example.com", passwordHash = "hash")
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername(user.username)).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        useCase().execute(requestFor(page, "released"), user.username)

        assertEquals("- [x] Deploy\n> released", page.contentMd)
    }

    @Test
    fun `trims outer summary whitespace before adding quote`() {
        val page = page(content = "- [ ] Deploy")
        val user = User(id = UUID.randomUUID(), username = "editor", email = "editor@example.com", passwordHash = "hash")
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername(user.username)).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }

        useCase().execute(requestFor(page, "\n done \n"), user.username)

        assertEquals("- [x] Deploy\n> done", page.contentMd)
    }

    @Test
    fun `rejects locked stale invalid and closed task snapshots`() {
        val lockedPage = page(content = "---\nlocked: true\n---\n- [ ] Deploy")
        whenever(pageRepository.findActiveByIdForUpdate(lockedPage.id!!)).thenReturn(lockedPage)
        val useCase = useCase()

        assertThrows<ForbiddenException> { useCase.execute(requestFor(lockedPage), "editor") }

        val stalePage = page(content = "- [ ] Deploy")
        whenever(pageRepository.findActiveByIdForUpdate(stalePage.id!!)).thenReturn(stalePage)
        assertThrows<ConflictException> {
            useCase.execute(requestFor(stalePage).copy(updatedAt = stalePage.updatedAt.minusSeconds(1)), "editor")
        }
        assertThrows<ConflictException> {
            useCase.execute(requestFor(stalePage).copy(sourceLine = "- [ ] Another task"), "editor")
        }

        val closedPage = page(content = "- [x] Deploy")
        whenever(pageRepository.findActiveByIdForUpdate(closedPage.id!!)).thenReturn(closedPage)
        assertThrows<ConflictException> { useCase.execute(requestFor(closedPage), "editor") }
    }

    @Test
    fun `second completion from same snapshot conflicts after locked reload`() {
        val page = page(content = "- [ ] Deploy")
        val snapshot = requestFor(page)
        val user = User(id = UUID.randomUUID(), username = "editor", email = "editor@example.com", passwordHash = "hash")
        whenever(pageRepository.findActiveByIdForUpdate(page.id!!)).thenReturn(page)
        whenever(userRepository.findByUsername(user.username)).thenReturn(user)
        whenever(pageRepository.save(any<Page>())).thenAnswer { it.arguments[0] }
        val useCase = useCase()

        useCase.execute(snapshot, user.username)

        assertThrows<ConflictException> { useCase.execute(snapshot, user.username) }
        verify(pageRepository, times(2)).findActiveByIdForUpdate(page.id!!)
        verify(pageRepository, times(1)).save(page)
    }

    private fun page(content: String) = Page(
        id = UUID.randomUUID(),
        slug = "deploy",
        title = "Deploy",
        contentMd = content,
        updatedAt = Instant.parse("2026-07-10T09:00:00Z")
    )

    private fun requestFor(page: Page, summary: String? = null) = CompleteOpenTaskRequest(
        documentId = page.id!!,
        updatedAt = page.updatedAt,
        sourceOffset = page.contentMd!!.indexOf("- ["),
        sourceLine = sourceLineAt(page.contentMd!!, page.contentMd!!.indexOf("- [")),
        summary = summary
    )

    private fun sourceLineAt(content: String, sourceOffset: Int): String {
        val lineStart = content.lastIndexOf('\n', sourceOffset - 1) + 1
        val lineEnd = content.indexOf('\n', sourceOffset).let { if (it == -1) content.length else it }
        return content.substring(lineStart, lineEnd).removeSuffix("\r")
    }
}
