package com.mdwiki.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CitationQuoteNormalizerTest {
    @Test
    fun `strips wiki and markdown link syntax`() {
        val quote = CitationQuoteNormalizer.normalize("See [[Alpha|A]] and [Beta](https://x.test).")
        assertThat(quote).isEqualTo("See A and Beta.")
    }

    @Test
    fun `truncates long quotes on a word boundary`() {
        val long = (1..80).joinToString(" ") { "word$it" }
        val quote = CitationQuoteNormalizer.normalize(long, maxChars = 40)
        assertThat(quote).endsWith("…")
        assertThat(quote.length).isLessThanOrEqualTo(42)
        assertThat(quote).doesNotContain("word80")
    }
}
