package com.mdwiki.integration

import com.mdwiki.controller.PkmController
import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.dto.CreatePageRequest
import com.mdwiki.dto.FolderDeletePageAction
import com.mdwiki.dto.MoveFolderRequest
import com.mdwiki.dto.TextCaptureRequest
import com.mdwiki.dto.UpdateFolderRequest
import com.mdwiki.dto.UpdatePageRequest
import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserPkmSettingsRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.FolderService
import com.mdwiki.service.PageService
import com.mdwiki.service.WikiFileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Transactional
class PkmOwnershipIntegrationTest {
    companion object {
        private val contentDir: Path = Files.createTempDirectory("mdwiki-pkm-ownership-")

        @JvmStatic
        @DynamicPropertySource
        fun contentDirectory(registry: DynamicPropertyRegistry) {
            registry.add("mdwiki.content-dir") { contentDir.toString() }
        }
    }

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var settings: UserPkmSettingsRepository
    @Autowired lateinit var controller: PkmController
    @Autowired lateinit var folderService: FolderService
    @Autowired lateinit var pageService: PageService
    @Autowired lateinit var wikiFiles: WikiFileService

    @Test
    fun `two user PKM flow is isolated and admin can manage owned content`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val alice = user("alice.foo-$suffix", "alice-$suffix@test", UserRole.EDITOR)
        val bob = user("alice_foo-$suffix", "bob-$suffix@test", UserRole.EDITOR)
        val admin = user("admin-$suffix", "admin-$suffix@test", UserRole.ADMIN)
        val aliceAuth = auth(alice)
        val bobAuth = auth(bob)

        val aliceCapture = controller.captureText(TextCaptureRequest("Alice capture"), aliceAuth)
        val bobCapture = controller.captureText(TextCaptureRequest("Bob capture"), bobAuth)
        val date = LocalDate.of(2026, 9, 7)
        val aliceDaily = controller.putDaily(date, aliceAuth)
        val bobDaily = controller.putDaily(date, bobAuth)
        val aliceSettings = settings.findById(alice.id!!).orElseThrow()
        val bobSettings = settings.findById(bob.id!!).orElseThrow()

        assertThat(aliceSettings.inboxFolder?.id).isNotEqualTo(bobSettings.inboxFolder?.id)
        assertThat(aliceSettings.dailyFolder?.id).isNotEqualTo(bobSettings.dailyFolder?.id)
        assertThat(aliceDaily.page.slug).contains(alice.id.toString()).isNotEqualTo(bobDaily.page.slug)
        assertThat(bobDaily.page.slug).contains(bob.id.toString())
        assertThat(wikiFiles.resolveFolderDirectory(aliceSettings.inboxFolder!!).toPath()
            .startsWith(contentDir.resolve(".pkm").resolve(alice.id.toString()))).isTrue()
        assertThat(wikiFiles.resolveFolderDirectory(bobSettings.dailyFolder!!).toPath()
            .startsWith(contentDir.resolve(".pkm").resolve(bob.id.toString()))).isTrue()

        assertThat(slugs(folderService.getTree(alice.username)))
            .contains(aliceCapture.page.slug, aliceDaily.page.slug)
            .doesNotContain(bobCapture.page.slug, bobDaily.page.slug)
        assertThat(slugs(folderService.getTree(bob.username)))
            .contains(bobCapture.page.slug, bobDaily.page.slug)
            .doesNotContain(aliceCapture.page.slug, aliceDaily.page.slug)

        val aliceInbox = aliceSettings.inboxFolder!!
        val child = folderService.create(CreateFolderRequest("Child", aliceInbox.id), alice.username)
        val target = folderService.create(CreateFolderRequest("Target", aliceInbox.id), alice.username)
        assertThrows<ForbiddenException> {
            folderService.create(CreateFolderRequest("Intruder", aliceInbox.id), bob.username)
        }
        assertThrows<ForbiddenException> {
            folderService.rename(child.id, UpdateFolderRequest("Stolen"), bob.username)
        }
        assertThrows<ForbiddenException> {
            folderService.move(child.id, MoveFolderRequest(target.id), bob.username)
        }
        assertThrows<ForbiddenException> {
            folderService.delete(child.id, bob.username, FolderDeletePageAction.DELETE)
        }
        assertThrows<ForbiddenException> {
            pageService.create(CreatePageRequest("intruder-$suffix", "Intruder", "", aliceInbox.id), bob.username)
        }
        val bobRootPage = pageService.create(
            CreatePageRequest("bob-root-$suffix", "Bob root", "", null),
            bob.username
        )
        assertThrows<ForbiddenException> {
            pageService.update(bobRootPage.slug, UpdatePageRequest(folderId = aliceInbox.id), bob.username)
        }

        folderService.rename(child.id, UpdateFolderRequest("Admin renamed"), admin.username)
        folderService.move(child.id, MoveFolderRequest(target.id), admin.username)
        val adminMovedPage = pageService.update(
            bobRootPage.slug,
            UpdatePageRequest(folderId = aliceInbox.id),
            admin.username
        )
        assertThat(adminMovedPage.folderId).isEqualTo(aliceInbox.id)
        val adminPage = pageService.create(
            CreatePageRequest("admin-owned-$suffix", "Admin", "", aliceInbox.id),
            admin.username
        )
        assertThat(adminPage.folderId).isEqualTo(aliceInbox.id)
        folderService.delete(child.id, admin.username)
    }

    private fun user(username: String, email: String, role: UserRole) =
        users.saveAndFlush(User(username = username, email = email, passwordHash = "x", role = role))

    private fun auth(user: User) = UsernamePasswordAuthenticationToken(user.username, "x")

    private fun slugs(nodes: List<com.mdwiki.dto.FolderTreeNode>): List<String> =
        nodes.flatMap { node -> listOfNotNull(node.slug) + slugs(node.children) }
}
