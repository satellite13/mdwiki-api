package com.mdwiki.integration

import com.mdwiki.dto.*
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Folder
import com.mdwiki.model.RevisionOperation
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.repository.FolderRepository
import com.mdwiki.service.PageRevisionService
import com.mdwiki.service.PageService
import com.mdwiki.service.StableSectionLinkService
import com.mdwiki.service.DeferredPageIndexer
import com.mdwiki.service.SearchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.mdwiki.rag.EmbeddingProvider

@SpringBootTest(properties = ["spring.datasource.hikari.maximum-pool-size=1"])
class Wave2PipelineIntegrationTest {
    companion object {
        private val contentDir: Path = Files.createTempDirectory("mdwiki-wave2-")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("mdwiki.content-dir") { contentDir.toString() }
        }
    }

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var pageService: PageService
    @Autowired lateinit var revisions: PageRevisionService
    @Autowired lateinit var stableLinks: StableSectionLinkService
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    @Autowired lateinit var folders: FolderRepository
    @Autowired lateinit var deferredPageIndexer: DeferredPageIndexer
    @MockitoBean lateinit var embeddingProvider: EmbeddingProvider
    @Autowired lateinit var searchService: SearchService

    private fun editor(prefix: String): User {
        val suffix = UUID.randomUUID().toString()
        return users.saveAndFlush(User(username = "$prefix-$suffix", email = "$prefix-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))
    }

    @Test
    fun `create edit rename import patch each append exactly one ordered revision and restore is nondestructive`() {
        val actor = editor("pipeline")
        val slug = "pipeline-${UUID.randomUUID()}"
        val created = pageService.create(CreatePageRequest(slug, "One", "alpha\n"), actor.username)
        val edited = pageService.update(slug, UpdatePageRequest(contentMd = "beta"), actor.username)
        val renamedSlug = "$slug-renamed"
        val renamed = pageService.update(slug,
            UpdatePageRequest(slug = renamedSlug, expectedUpdatedAt = edited.updatedAt), actor.username)
        pageService.importMd(listOf(ImportMdFileInput("$renamedSlug.md", "# Imported\nbody")),
            null, true, actor.username)
        val imported = pageService.findBySlug(renamedSlug)
        pageService.patch(renamedSlug,
            PatchPageRequest("body", "patched", imported.updatedAt), actor.username)

        val page = pages.findBySlugAndDeletedAtIsNull(renamedSlug)!!
        assertThat(revisions.list(page, 20, null).map { it.operation }).containsExactly(
            RevisionOperation.PATCH, RevisionOperation.IMPORT, RevisionOperation.RENAME,
            RevisionOperation.EDIT, RevisionOperation.CREATE
        )
        assertThat(revisions.list(page, 2, null).map { it.revisionNo }).containsExactly(5, 4)
        assertThat(revisions.list(page, 20, 4).map { it.revisionNo }).containsExactly(3, 2, 1)

        val current = pageService.findBySlug(renamedSlug)
        val restored = pageService.restoreRevision(renamedSlug,
            RestoreRevisionRequest(1, current.updatedAt), actor.username)
        assertThat(restored.slug).isEqualTo(renamedSlug)
        assertThat(restored.folderId).isEqualTo(current.folderId)
        assertThat(restored.contentMd).isEqualTo(created.contentMd)
        assertThat(revisions.list(page, 1, null).single().operation).isEqualTo(RevisionOperation.RESTORE)
        assertThatThrownBy {
            pageService.restoreRevision(renamedSlug, RestoreRevisionRequest(1, current.updatedAt), actor.username)
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `stable links materialize once follow slug and retire only when explicit id disappears`() {
        val actor = editor("stable")
        val slug = "stable-${UUID.randomUUID()}"
        val created = pageService.create(CreatePageRequest(slug, "Stable", "## Same\none\n## Same\ntwo"), actor.username)
        val map = pageService.mapSections(slug)
        val second = map.sections.filter { it.heading == "Same" }[1]
        val first = stableLinks.materialize(slug, StableLinkRequest(second.key, created.updatedAt), actor.username)
        val countAfterFirst = revisions.list(pages.findBySlugAndDeletedAtIsNull(slug)!!, 100, null).size
        val repeated = stableLinks.materialize(slug,
            StableLinkRequest(first.sectionKey, first.updatedAt), actor.username)
        assertThat(repeated.stableId).isEqualTo(first.stableId)
        assertThat(revisions.list(pages.findBySlugAndDeletedAtIsNull(slug)!!, 100, null)).hasSize(countAfterFirst)

        val current = pageService.findBySlug(slug)
        val renamedSlug = "$slug-moved"
        pageService.update(slug, UpdatePageRequest(slug = renamedSlug,
            expectedUpdatedAt = current.updatedAt), actor.username)
        assertThat(stableLinks.resolve(first.stableId).pageSlug).isEqualTo(renamedSlug)

        val moved = pageService.findBySlug(renamedSlug)
        pageService.update(renamedSlug, UpdatePageRequest(contentMd = "## Same\none",
            expectedUpdatedAt = moved.updatedAt), actor.username)
        assertThatThrownBy { stableLinks.resolve(first.stableId) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `concurrent revision allocation stays unique and gapless`() {
        val actor = editor("concurrent")
        val slug = "concurrent-${UUID.randomUUID()}"
        pageService.create(CreatePageRequest(slug, "Concurrent", "same"), actor.username)
        val executor = Executors.newFixedThreadPool(6)
        val futures = (1..12).map {
            executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    val page = pages.findBySlugAndDeletedAtIsNull(slug)!!
                    revisions.record(page, actor.username, RevisionOperation.EDIT)
                }
            }
        }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        val numbers = revisions.list(pages.findBySlugAndDeletedAtIsNull(slug)!!, 100, null)
            .map { it.revisionNo }.sorted()
        assertThat(numbers).containsExactlyElementsOf((1L..13L).toList())
    }

    @Test
    fun `materialize adopts a unique explicit id and rejects duplicate explicit ids`() {
        val actor = editor("adopt")
        val slug = "adopt-${UUID.randomUUID()}"
        val explicitId = "custom_${UUID.randomUUID().toString().replace("-", "")}"
        val created = pageService.create(CreatePageRequest(slug, "Adopt", "## One {#$explicitId}\nbody"), actor.username)
        val adopted = stableLinks.materialize(slug,
            StableLinkRequest(explicitId, created.updatedAt), actor.username)
        assertThat(adopted.stableId).isEqualTo(explicitId)
        assertThat(revisions.list(pages.findBySlugAndDeletedAtIsNull(slug)!!, 100, null)).hasSize(1)

        val duplicateSlug = "duplicate-${UUID.randomUUID()}"
        val duplicateId = "same_${UUID.randomUUID().toString().replace("-", "")}"
        val duplicate = pageService.create(CreatePageRequest(duplicateSlug, "Duplicate",
            "## One {#$duplicateId}\na\n## Two {#$duplicateId}\nb"), actor.username)
        val second = pageService.mapSections(duplicateSlug).sections.last().key
        assertThatThrownBy {
            stableLinks.materialize(duplicateSlug,
                StableLinkRequest(second, duplicate.updatedAt), actor.username)
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `restore and stable materialization enforce owned folder mutation boundary`() {
        val owner = editor("owner")
        val intruder = editor("intruder")
        val folder = folders.saveAndFlush(Folder(name = "Private-${UUID.randomUUID()}", owner = owner, createdBy = owner))
        val slug = "owned-${UUID.randomUUID()}"
        val created = pageService.create(CreatePageRequest(slug, "Owned", "## Secret\none", folder.id), owner.username)
        val edited = pageService.update(slug,
            UpdatePageRequest(contentMd = "## Secret\ntwo", expectedUpdatedAt = created.updatedAt), owner.username)

        assertThatThrownBy {
            pageService.restoreRevision(slug, RestoreRevisionRequest(1, edited.updatedAt), intruder.username)
        }.isInstanceOf(ForbiddenException::class.java)
        val key = pageService.mapSections(slug).sections.single { it.heading == "Secret" }.key
        assertThatThrownBy {
            stableLinks.materialize(slug, StableLinkRequest(key, edited.updatedAt), intruder.username)
        }.isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `soft delete and trash restore append exactly one deleted-state revision and enforce ownership`() {
        val owner = editor("trash-owner")
        val intruder = editor("trash-intruder")
        val folder = folders.saveAndFlush(Folder(name = "Trash-${UUID.randomUUID()}", owner = owner, createdBy = owner))
        val slug = "trash-${UUID.randomUUID()}"
        pageService.create(CreatePageRequest(slug, "Trash", "kept", folder.id), owner.username)

        assertThatThrownBy {
            pageService.delete(slug, com.mdwiki.service.usecase.DeletePageUseCase.DeleteMode.SOFT, intruder.username)
        }.isInstanceOf(ForbiddenException::class.java)
        pageService.delete(slug, com.mdwiki.service.usecase.DeletePageUseCase.DeleteMode.SOFT, owner.username)
        pageService.delete(slug, com.mdwiki.service.usecase.DeletePageUseCase.DeleteMode.SOFT, owner.username)
        val deleted = pages.findBySlug(slug)!!
        var history = revisions.list(deleted, 10, null)
        assertThat(history.map { it.operation }).containsExactly(
            RevisionOperation.DELETE, RevisionOperation.CREATE
        )
        assertThat(revisions.get(deleted, history.first().revisionNo).deletedAt).isNotNull()

        pageService.restore(slug, owner.username)
        history = revisions.list(pages.findBySlugAndDeletedAtIsNull(slug)!!, 10, null)
        assertThat(history.map { it.operation }).containsExactly(
            RevisionOperation.RESTORE_TRASH, RevisionOperation.DELETE, RevisionOperation.CREATE
        )
        assertThat(revisions.get(pages.findBySlugAndDeletedAtIsNull(slug)!!,
            history.first().revisionNo).deletedAt).isNull()
    }

    @Test
    fun `after commit indexing does not retain the only pool connection`() {
        val actor = editor("pool-one")
        val slug = "pool-one-${UUID.randomUUID()}"
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10)) {
            pageService.create(CreatePageRequest(slug, "Pool one", ""), actor.username)
            assertThat(deferredPageIndexer.awaitIdle(java.time.Duration.ofSeconds(5))).isTrue()
        }
    }

    @Test
    fun `text and rag search apply all requested tags before limit`() {
        val actor = editor("tag-search")
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val one = "one$suffix"
        val two = "two$suffix"
        pageService.create(CreatePageRequest("tag-both-$suffix", "Needle both",
            "needleunique #$one #$two"), actor.username)
        pageService.create(CreatePageRequest("tag-one-$suffix", "Needle one",
            "needleunique #$one"), actor.username)
        assertThat(deferredPageIndexer.awaitIdle(java.time.Duration.ofSeconds(5))).isTrue()

        assertThat(searchService.search("needleunique", tags = listOf(one, two)).map { it.slug })
            .containsExactly("tag-both-$suffix")
        assertThat(searchService.ragSearch("needleunique", 10, listOf(one, two)).map { it.pageSlug })
            .allMatch { it == "tag-both-$suffix" }
    }
}
