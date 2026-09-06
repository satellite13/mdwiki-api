package com.mdwiki.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RevisionDiffServiceTest {
    private val service = RevisionDiffService()

    @Test
    fun `diff preserves unicode and trailing newline changes`() {
        val result = service.diff("Привет\nмир\n", "Привет\nмир!")

        assertThat(result.rows).containsExactly(
            RevisionDiffRow("CONTEXT", "Привет", "Привет"),
            RevisionDiffRow("REMOVE", "мир", null),
            RevisionDiffRow("ADD", null, "мир!"),
            RevisionDiffRow("REMOVE", "", null)
        )
    }

    @Test
    fun `diff falls back without quadratic allocation for large documents`() {
        val before = (1..10_001).joinToString("\n") { "before-$it" }
        val after = (1..10_001).joinToString("\n") { "after-$it" }

        val result = service.diff(before, after)

        assertThat(result.truncated).isTrue()
        assertThat(result.rows).isNotEmpty
    }
}
