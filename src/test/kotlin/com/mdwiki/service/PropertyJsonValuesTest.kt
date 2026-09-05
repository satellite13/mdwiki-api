package com.mdwiki.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PropertyJsonValuesTest {
    private val json = ObjectMapper()
    private val yaml = YAMLMapper()

    @Test
    fun `frontmatter scalar serializes as plain JSON value not JsonNode bean flags`() {
        val node = yaml.readTree("title: Hello wiki\ncount: 3\n")
        val payload = mapOf(
            "title" to PropertyJsonValues.toWire(node.get("title")),
            "count" to PropertyJsonValues.toWire(node.get("count"))
        )
        val encoded = json.writeValueAsString(payload)
        assertEquals("""{"title":"Hello wiki","count":3}""", encoded)
        assertFalse(encoded.contains("\"textual\""))
        assertFalse(encoded.contains("\"nodeType\""))
    }
}
