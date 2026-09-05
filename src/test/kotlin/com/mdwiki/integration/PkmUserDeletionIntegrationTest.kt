package com.mdwiki.integration

import com.mdwiki.dto.TextCaptureRequest
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.UserPkmSettingsRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.PkmService
import com.mdwiki.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
class PkmUserDeletionIntegrationTest {
    companion object {
        private val contentDir: Path = Files.createTempDirectory("mdwiki-user-delete-")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("mdwiki.content-dir") { contentDir.toString() }
        }
    }

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var settings: UserPkmSettingsRepository
    @Autowired lateinit var folders: FolderRepository
    @Autowired lateinit var pkm: PkmService
    @Autowired lateinit var userService: UserService

    @Test
    fun `deleting user removes PKM rows folders pages and physical owner scope`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val target = users.saveAndFlush(User(username = "remove-$suffix", email = "remove-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))
        val admin = users.saveAndFlush(User(username = "admin-remove-$suffix", email = "admin-remove-$suffix@test",
            passwordHash = "x", role = UserRole.ADMIN))
        pkm.captureText(TextCaptureRequest("Delete me"), target.username)
        pkm.captureImage(
            MockMultipartFile("file", "delete.png", "image/png",
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
            null, null, target.username
        )
        pkm.putDaily(LocalDate.of(2026, 9, 9), target.username)
        val ownerPath = contentDir.resolve(".pkm").resolve(target.id.toString())
        assertThat(ownerPath).exists()

        userService.delete(target.id!!, admin.username)

        assertThat(users.findById(target.id!!)).isEmpty()
        assertThat(settings.findById(target.id!!)).isEmpty()
        assertThat(folders.findAll().none { it.owner?.id == target.id }).isTrue()
        assertThat(ownerPath).doesNotExist()
    }
}
