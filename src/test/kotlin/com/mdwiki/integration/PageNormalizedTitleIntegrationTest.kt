package com.mdwiki.integration

import com.mdwiki.model.Page
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Интеграционная проверка колонки normalized_title (миграция 002):
 * JPA-хук заполняет её на INSERT/UPDATE, findFirstByNormalizedTitle находит equality-запросом.
 * Работает против PostgreSQL из docker-compose (:54328), каждый тест откатывается.
 */
@SpringBootTest
@Transactional
class PageNormalizedTitleIntegrationTest {

    @Autowired
    private lateinit var pageRepository: PageRepository

    @Test
    fun `normalized title is filled on save and searchable`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val page = pageRepository.save(
            Page(slug = "it-norm-$suffix", title = "MCP протокол $suffix")
        )

        val found = pageRepository.findFirstByNormalizedTitle("mcp-протокол-$suffix")

        assertNotNull(found)
        assertEquals(page.id, found!!.id)
    }

    @Test
    fun `normalized title follows title updates`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val page = pageRepository.save(
            Page(slug = "it-norm-upd-$suffix", title = "Старый заголовок $suffix")
        )

        page.title = "Новый заголовок $suffix"
        pageRepository.save(page)

        assertNull(pageRepository.findFirstByNormalizedTitle("старый-заголовок-$suffix"))
        val found = pageRepository.findFirstByNormalizedTitle("новый-заголовок-$suffix")
        assertNotNull(found)
        assertEquals(page.id, found!!.id)
    }
}
