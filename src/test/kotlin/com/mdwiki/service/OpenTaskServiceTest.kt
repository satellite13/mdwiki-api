package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OpenTaskServiceTest {
    @Mock private lateinit var pageRepository: PageRepository

    private val frontmatterMetaService = FrontmatterMetaService()

    @Test
    fun `lists open tasks with snapshot coordinates and skips fenced code`() {
        val updatedAt = Instant.parse("2026-07-10T09:00:00Z")
        val page = Page(
            id = UUID.randomUUID(),
            slug = "roadmap",
            title = "Database title",
            contentMd = """
                ---
                title: Roadmap
                locked: true
                ---
                - [ ] Ship API
                - [x] Already done
                ```md
                - [ ] Example only
                ```
                - [ ] Ship UI
            """.trimIndent(),
            updatedAt = updatedAt
        )
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(page))

        val tasks = OpenTaskService(pageRepository, frontmatterMetaService).listOpenTasks("reader")

        assertEquals(2, tasks.size)
        assertEquals(page.id, tasks[0].documentId)
        assertEquals("roadmap", tasks[0].slug)
        assertEquals("Roadmap", tasks[0].documentTitle)
        assertEquals("Ship API", tasks[0].text)
        assertEquals(page.contentMd!!.indexOf("- [ ] Ship API"), tasks[0].sourceOffset)
        assertEquals("- [ ] Ship API", tasks[0].sourceLine)
        assertEquals(updatedAt, tasks[0].updatedAt)
        assertEquals(true, tasks[0].locked)
        assertEquals("Ship UI", tasks[1].text)
    }

    @Test
    fun `does not close a fence with trailing text`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "fences",
            title = "Fences",
            contentMd = """
                ```md
                - [ ] First example
                ```text
                - [ ] Still an example
                ```
                - [ ] Real task
            """.trimIndent()
        )
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(page))

        val tasks = OpenTaskService(pageRepository, frontmatterMetaService).listOpenTasks("reader")

        assertEquals(listOf("Real task"), tasks.map { it.text })
    }

    @Test
    fun `does not close a fence with indented markers`() {
        val page = Page(
            id = UUID.randomUUID(),
            slug = "indented-fences",
            title = "Indented fences",
            contentMd = "```md\n- [ ] First example\n    ```\n- [ ] Still an example\n\t```\n- [ ] Another example\n```\n- [ ] Real task"
        )
        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(page))

        val tasks = OpenTaskService(pageRepository, frontmatterMetaService).listOpenTasks("reader")

        assertEquals(listOf("Real task"), tasks.map { it.text })
    }
}
