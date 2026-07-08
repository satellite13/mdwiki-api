package com.mdwiki.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WikilinkEscapeLeakTest {
    private val svc = WikilinkService()

    @Test
    fun `extractWikilinks should not treat escaped wikilinks as active links`() {
        val leaks = mutableListOf<String>()
        val cases = listOf(
            "Use `[[wikilinks]]` syntax",
            "Use ``[[wikilinks]]`` syntax",
            """Use `` `[[wikilinks]]` `` syntax""",
            "Use \\[\\[wikilinks\\]\\] syntax",
            "<code>[[wikilinks]]</code>",
            "<pre>[[wikilinks]]</pre>",
            "    [[wikilinks]]",
            "```\n[[wikilinks]]\n```",
            "~~~\n[[wikilinks]]\n~~~",
            "- `[[wikilinks]]`",
            "| `[[wikilinks]]` | x |\n| --- | --- |",
            "> `[[wikilinks]]`",
            "`[[wikilinks]]` and [[real-broken]]",
        )

        for (md in cases) {
            val slugs = svc.extractWikilinks(md).map { it.slug }
            if ("wikilinks" in slugs) {
                leaks += md
            }
        }

        assertEquals(emptyList<String>(), leaks, "These cases incorrectly extract wikilinks slug")
    }
}
