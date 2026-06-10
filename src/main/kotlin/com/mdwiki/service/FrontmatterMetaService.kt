package com.mdwiki.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mdwiki.model.Page
import com.mdwiki.util.MarkdownFrontmatter
import org.springframework.stereotype.Service

@Service
class FrontmatterMetaService {

    private val yamlMapper = YAMLMapper().apply { registerKotlinModule() }

    /**
     * Парсит YAML из frontmatter документа в [JsonNode] и записывает в сущность.
     * При отсутствии блока, пустом YAML или ошибке парсинга — `null`.
     */
    fun refreshFromContent(page: Page, contentMd: String?) {
        page.frontmatterMeta = parseToJson(contentMd)
    }

    fun parseToJson(contentMd: String?): JsonNode? {
        if (contentMd.isNullOrBlank()) return null
        val yaml = MarkdownFrontmatter.extractYamlInner(contentMd) ?: return null
        if (yaml.isBlank()) return null
        return try {
            yamlMapper.readTree(yaml.byteInputStream(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Проверяет, заблокирована ли страница для редактирования (read-only).
     * Проверка идёт по frontmatter — если в нём есть `locked: true`, страница locked.
     * Если frontmatterMeta ещё не сохранена в БД, парсит из переданного содержимого.
     */
    fun isLocked(page: com.mdwiki.model.Page): Boolean {
        val meta = page.frontmatterMeta
            ?: parseToJson(page.contentMd)
            ?: return false
        val lockedNode = meta.get("locked")
        return lockedNode != null && lockedNode.isBoolean && lockedNode.booleanValue()
    }
}
