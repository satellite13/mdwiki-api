package com.mdwiki.integration

import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.FolderService
import com.mdwiki.service.PageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Reproduces LazyInitializationException when building the folder tree with
 * `spring.jpa.open-in-view=false`: pages carry a lazy `folder`, and visibility
 * checks touch `folder.owner` after the repository transaction has closed.
 */
@SpringBootTest
class FolderTreeLazyLoadingIntegrationTest {
    companion object {
        private val contentDir: Path = Files.createTempDirectory("mdwiki-folder-tree-lazy-")

        @JvmStatic
        @DynamicPropertySource
        fun contentDirectory(registry: DynamicPropertyRegistry) {
            registry.add("mdwiki.content-dir") { contentDir.toString() }
        }
    }

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var folderService: FolderService
    @Autowired lateinit var pageService: PageService
    @Autowired lateinit var transactions: TransactionTemplate

    @Test
    fun `getTree resolves lazy folder owner outside open-in-view`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "tree-$suffix"
        transactions.executeWithoutResult {
            users.save(
                User(
                    username = username,
                    email = "$username@test",
                    passwordHash = "x",
                    role = UserRole.EDITOR
                )
            )
            val folder = folderService.create(CreateFolderRequest("Docs-$suffix"), username)
            pageService.create(
                CreatePageRequest(
                    slug = "nested-$suffix",
                    title = "Nested-$suffix",
                    contentMd = "# Nested",
                    folderId = folder.id
                ),
                username
            )
        }

        val tree = assertDoesNotThrow { folderService.getTree(username) }
        assertThat(slugs(tree)).contains("nested-$suffix")
    }

    private fun slugs(nodes: List<com.mdwiki.dto.FolderTreeNode>): Set<String> =
        nodes.flatMapTo(mutableSetOf()) { node ->
            buildSet {
                node.slug?.let { add(it) }
                addAll(slugs(node.children))
            }
        }
}
