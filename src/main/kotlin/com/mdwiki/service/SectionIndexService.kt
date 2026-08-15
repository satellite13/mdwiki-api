package com.mdwiki.service

import com.mdwiki.model.Page
import com.mdwiki.model.PageSection
import com.mdwiki.repository.PageSectionRepository
import com.mdwiki.util.MarkdownSectionParser
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant

@Service
class SectionIndexService(
    private val pageSectionRepository: PageSectionRepository
) {
    fun rebuild(page: Page, contentMd: String = page.contentMd ?: ""): List<PageSection> {
        val pageId = page.id ?: return emptyList()
        val parsed = MarkdownSectionParser.parse(contentMd)
        val existing = pageSectionRepository.findByPageIdOrderBySortOrder(pageId).toMutableList()
        val byKey = existing.associateBy { it.stableKey }.toMutableMap()
        val byPath = existing.groupBy { it.headingPath }.mapValues { it.value.toMutableList() }
        val claimed = mutableSetOf<PageSection>()
        val upserts = mutableListOf<PageSection>()
        val now = Instant.now()

        for (section in parsed) {
            val matched = byKey.remove(section.stableKey)
                ?: byPath[section.headingPath]
                    ?.firstOrNull { it !in claimed }
                    ?.also { byKey.remove(it.stableKey) }
            val row = matched ?: PageSection(
                page = page,
                stableKey = section.stableKey,
                heading = section.heading,
                headingLevel = section.headingLevel,
                headingPath = section.headingPath,
                sortOrder = section.sortOrder,
                startOffset = section.startOffset,
                endOffset = section.endOffset,
                contentHash = hashOf(contentMd, section.startOffset, section.endOffset),
                updatedAt = now
            )
            if (matched != null) {
                claimed.add(matched)
                row.stableKey = section.stableKey
                row.heading = section.heading
                row.headingLevel = section.headingLevel
                row.headingPath = section.headingPath
                row.sortOrder = section.sortOrder
                row.startOffset = section.startOffset
                row.endOffset = section.endOffset
                row.contentHash = hashOf(contentMd, section.startOffset, section.endOffset)
                row.updatedAt = now
            }
            upserts.add(row)
        }

        val stale = existing.filter { it !in claimed && it !in upserts }
        if (stale.isNotEmpty()) {
            pageSectionRepository.deleteAll(stale)
        }
        return pageSectionRepository.saveAll(upserts).toList()
    }

    fun listOrRebuild(page: Page): List<PageSection> {
        val pageId = page.id ?: return emptyList()
        val content = page.contentMd ?: ""
        val existing = pageSectionRepository.findByPageIdOrderBySortOrder(pageId)
        if (existing.isNotEmpty() && matchesLiveContent(existing, content)) return existing
        return rebuild(page, content)
    }

    private fun matchesLiveContent(existing: List<PageSection>, content: String): Boolean {
        val parsed = MarkdownSectionParser.parse(content)
        if (existing.size != parsed.size) return false
        return existing.zip(parsed).all { (row, section) ->
            row.stableKey == section.stableKey &&
                row.startOffset == section.startOffset &&
                row.endOffset == section.endOffset &&
                row.contentHash == hashOf(content, section.startOffset, section.endOffset)
        }
    }

    companion object {
        fun hashOf(content: String, start: Int, end: Int): String {
            val slice = content.substring(start.coerceAtLeast(0), end.coerceAtMost(content.length))
            val digest = MessageDigest.getInstance("SHA-256").digest(slice.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
