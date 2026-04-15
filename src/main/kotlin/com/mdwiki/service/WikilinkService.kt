package com.mdwiki.service

import org.springframework.stereotype.Service

@Service
class WikilinkService {

    data class Wikilink(val slug: String, val displayText: String?)

    private val wikilinkPattern = Regex("""\[\[([^|\]]+?)(?:\|([^\]]+?))?\]\]""")
    private val tagPattern = Regex("""(?<=\s|^)#([\w\p{L}-]+)""")
    private val codeBlockPattern = Regex("""(`[^`]+`|```[\s\S]*?```)""")

    fun extractWikilinks(markdown: String): List<Wikilink> {
        return wikilinkPattern.findAll(markdown).map { match ->
            Wikilink(
                slug = match.groupValues[1].trim(),
                displayText = match.groupValues[2].trim().ifEmpty { null }
            )
        }.toList()
    }

    fun extractTags(markdown: String): Set<String> {
        val cleaned = codeBlockPattern.replace(markdown, "")
        return tagPattern.findAll(cleaned).map { it.groupValues[1] }.toSet()
    }
}
