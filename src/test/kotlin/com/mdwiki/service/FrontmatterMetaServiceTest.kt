package com.mdwiki.service

import com.mdwiki.model.Page
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class FrontmatterMetaServiceTest {

    private val service = FrontmatterMetaService()

    @Test
    fun `parseToJson maps yaml to json structure`() {
        val md = """
            |---
            |title: Hello
            |draft: true
            |tags: [a, b]
            |---
            |# Body
        """.trimMargin()
        val node = service.parseToJson(md)!!
        assertEquals("Hello", node.get("title").asText())
        assertTrue(node.get("draft").asBoolean())
        assertTrue(node.get("tags").isArray)
        assertEquals(2, node.get("tags").size())
    }

    @Test
    fun `parseToJson returns null without frontmatter`() {
        assertNull(service.parseToJson("# Only body"))
    }

    @Test
    fun `refreshFromContent writes to page`() {
        val page = Page(id = UUID.randomUUID(), slug = "x", title = "X")
        val md = "---\nfoo: 1\n---\n\nok"
        service.refreshFromContent(page, md)
        assertEquals(1, page.frontmatterMeta!!.get("foo").asInt())
    }

    @Test
    fun `invalid yaml clears to null`() {
        val page = Page(id = UUID.randomUUID(), slug = "x", title = "X", contentMd = "was")
        page.frontmatterMeta = service.parseToJson("---\nfoo: 1\n---\n")
        val broken = "---\nfoo: [\n---\n"
        service.refreshFromContent(page, broken)
        assertNull(page.frontmatterMeta)
    }
}
