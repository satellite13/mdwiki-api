package com.mdwiki.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.nio.file.Files
import java.nio.file.Path

/**
 * Полный контекст + только переопределение [mdwiki.content-dir] (без лишнего @Bean WikiProperties),
 * чтобы не дублировать бин и не ломать остальные @SpringBootTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UploadControllerTest {

    companion object {
        private val contentDir: Path = Files.createTempDirectory("mdwiki-uploads-int")

        @JvmStatic
        @DynamicPropertySource
        fun registerContentDir(registry: DynamicPropertyRegistry) {
            registry.add("mdwiki.content-dir") { contentDir.toString() }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            if (Files.exists(contentDir)) {
                Files.walk(contentDir).sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `upload image writes file and returns url`() {
        mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "a.png", "image/png", byteArrayOf(10, 11)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.url") { exists() }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `upload empty file is bad request`() {
        mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "a.png", "image/png", ByteArray(0)))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `upload rejects non image content type`() {
        mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "a.bin", "application/octet-stream", byteArrayOf(1)))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `get file is permitted without authentication`() {
        Files.createDirectories(contentDir.resolve("uploads"))
        val name = "abc123.png"
        Files.write(contentDir.resolve("uploads").resolve(name), byteArrayOf(9, 9))

        mockMvc.get("/api/uploads/$name").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `get file rejects dot dot in name`() {
        mockMvc.get("/api/uploads/foo..bar.png").andExpect {
            status { isNotFound() }
        }
    }
}
