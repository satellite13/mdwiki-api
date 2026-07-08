package com.mdwiki.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WikilinkEscapeCasesTest {
    private val svc = WikilinkService()

    @Test
    fun `extractWikilinks ignores wikilinks in common escape contexts`() {
        val cases = mapOf(
            "inline single backticks" to "See `[[wikilinks]]` syntax and [[real]]",
            "fenced block" to "See:\n```\n[[wikilinks]]\n```\nAlso [[real]]",
            "indented code" to "Text\n    [[wikilinks]]\nAlso [[real]]",
            "html code" to "<code>[[wikilinks]]</code> and [[real]]",
            "backslash escaped" to """\[\[wikilinks\]\] and [[real]]""",
            "double backtick span" to """See ``[[wikilinks]]`` and [[real]]""",
            "nested backtick span" to """Use `` `[[wikilinks]]` `` and [[real]]""",
            "tilde fence" to "See:\n~~~\n[[wikilinks]]\n~~~\nAlso [[real]]",
            "table cell code" to "| syntax | desc |\n| --- | --- |\n| `[[wikilinks]]` | example |\n\n[[real]]",
            "list item code" to "- `[[wikilinks]]`\n- [[real]]",
        )

        for ((name, md) in cases) {
            val links = svc.extractWikilinks(md).map { it.slug }
            assertEquals(listOf("real"), links, "Failed for case: $name\nmd=$md")
        }
    }
}
