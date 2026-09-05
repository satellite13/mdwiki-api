package com.mdwiki.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnlinkedMentionScannerTest {
    @Test
    fun `finds unicode mentions and repeated occurrences`() {
        val markdown = "Проект Ёжик и ещё ёЖИК."

        val matches = UnlinkedMentionScanner.scan(markdown, "Ёжик")

        assertThat(matches.map { markdown.substring(it.startOffset, it.endOffset) })
            .containsExactly("Ёжик", "ёЖИК")
    }

    @Test
    fun `excludes frontmatter code and existing links`() {
        val markdown = """
            ---
            title: Target
            ---
            `Target`
            ```
            Target
            ```
            [[target|Target]]
            [Target](/page/target)
            Plain Target.
        """.trimIndent()

        val matches = UnlinkedMentionScanner.scan(markdown, "Target")

        assertThat(matches).hasSize(1)
        assertThat(markdown.substring(matches.single().startOffset, matches.single().endOffset))
            .isEqualTo("Target")
    }
}
