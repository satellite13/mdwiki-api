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
            [Target][reference]
            [reference]: /page/target
            ``Target ` nested``
            [nested [Target] label](https://example.test)
            <https://example.test/Target>
        """.trimIndent()

        val matches = UnlinkedMentionScanner.scan(markdown, "Target")

        assertThat(matches).hasSize(1)
        assertThat(markdown.substring(matches.single().startOffset, matches.single().endOffset))
            .isEqualTo("Target")
    }

    @Test
    fun `uses unicode token boundaries without losing repeats`() {
        val markdown = "Art Article Art, Кот Котик кот."

        assertThat(UnlinkedMentionScanner.scan(markdown, "Art").map { it.startOffset })
            .containsExactly(0, 12)
        assertThat(UnlinkedMentionScanner.scan(markdown, "Кот").map {
            markdown.substring(it.startOffset, it.endOffset)
        }).containsExactly("Кот", "кот")
    }

    @Test
    fun `supports variable fenced delimiters`() {
        val markdown = """
            ````kotlin
            Target
            ```
            Target
            ````
            Target
        """.trimIndent()

        val matches = UnlinkedMentionScanner.scan(markdown, "Target")

        assertThat(matches).hasSize(1)
        assertThat(markdown.substring(matches.single().startOffset, matches.single().endOffset)).isEqualTo("Target")
    }

    @Test
    fun `matches supplementary unicode letters with code point boundaries`() {
        val upper = "\uD801\uDC00" // DESERET CAPITAL LETTER LONG I
        val lower = "\uD801\uDC28" // DESERET SMALL LETTER LONG I
        val markdown = "$lower X${lower}X $upper"

        val matches = UnlinkedMentionScanner.scan(markdown, upper)

        assertThat(matches.map { markdown.substring(it.startOffset, it.endOffset) })
            .containsExactly(lower, upper)
    }
}
