package com.mdwiki.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.mdwiki.dto.*
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.UnprocessableEntityException
import com.mdwiki.model.*
import com.mdwiki.repository.*
import com.mdwiki.util.MarkdownFrontmatter
import com.mdwiki.util.PersistentInstant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

@Service
class PropertyService(
    private val definitions: PropertyDefinitionRepository,
    private val values: PagePropertyValueRepository,
    private val pages: PageRepository,
    private val users: UserRepository,
    private val folderAccess: FolderAccessPolicy,
    private val mapper: ObjectMapper
) {
    private val yaml = YAMLMapper()

    @Transactional(readOnly = true)
    fun listDefinitions() = definitions.findAllByDeletedAtIsNullOrderByDisplayNameAsc().map(::response)

    @Transactional
    fun create(request: PropertyDefinitionWriteRequest, username: String): PropertyDefinitionResponse {
        val key = request.key.trim()
        if (!Regex("[A-Za-z][A-Za-z0-9_-]{0,99}").matches(key)) bad("Property key must be an ASCII identifier")
        if (definitions.existsByKeyIgnoreCaseAndDeletedAtIsNull(key)) throw ConflictException("Property key already exists")
        val config = validateConfig(request.type, request.config ?: mapper.createObjectNode())
        val actor = users.findByUsername(username) ?: throw NotFoundException("User not found")
        return response(definitions.save(PropertyDefinition(key = key, displayName = request.displayName.trim(), type = request.type, config = config, required = request.required, createdBy = actor)))
    }

    @Transactional
    fun update(id: java.util.UUID, request: PropertyDefinitionWriteRequest): PropertyDefinitionResponse {
        val definition = definitions.findByIdAndDeletedAtIsNull(id) ?: throw NotFoundException("Property not found")
        if (request.key != definition.key || request.type != definition.type) bad("Property key and type are immutable")
        if (request.expectedVersion != null && request.expectedVersion != definition.version) throw ConflictException("Property definition changed")
        val config = validateConfig(definition.type, request.config ?: definition.config)
        if ((definition.type == PropertyType.SELECT || definition.type == PropertyType.MULTI_SELECT) && removedOptions(definition.config, config).isNotEmpty()) {
            val removed = removedOptions(definition.config, config)
            if (values.findAll().any { it.property.id == definition.id && it.valueJson.any { node -> node.asText() in removed } }) bad("Cannot remove an option that is in use")
        }
        definition.displayName = request.displayName.trim()
        definition.config = config
        definition.required = request.required
        definition.updatedAt = PersistentInstant.now()
        return response(definition)
    }

    @Transactional
    fun delete(id: java.util.UUID) {
        val definition = active(id)
        definition.deletedAt = PersistentInstant.now()
        definition.updatedAt = PersistentInstant.now()
    }

    @Transactional(readOnly = true)
    fun pageProperties(slug: String, username: String): PagePropertiesResponse {
        val page = pages.findBySlugAndDeletedAtIsNull(slug) ?: throw NotFoundException("Page not found: $slug")
        page.folder?.let { folderAccess.requireAccess(it, username) } ?: folderAccess.actor(username)
        val defs = definitions.findAllByDeletedAtIsNullOrderByDisplayNameAsc()
        val parsed = parsedFrontmatter(page.contentMd)
        val known = defs.associate { it.key to (parsed.get(it.key) ?: mapper.nullNode()) }.filterValues { !it.isNull }
        val unknown = parsed.fields().asSequence().filter { it.key !in defs.map(PropertyDefinition::key).toSet() }.associate { it.key to it.value }
        return PagePropertiesResponse(defs.map(::response), known, unknown)
    }

    @Transactional
    fun patchPage(page: Page, request: PatchPagePropertiesRequest, username: String): String {
        page.folder?.let { folderAccess.requireAccess(it, username) }
        if (!PersistentInstant.same(page.updatedAt, request.expectedUpdatedAt)) throw ConflictException("Page has changed")
        val defs = definitions.findAllByDeletedAtIsNullOrderByDisplayNameAsc().associateBy { it.key }
        val set = linkedMapOf<String, String>()
        val remove = mutableListOf<String>()
        request.operations.forEach { operation ->
            val definition = defs[operation.key] ?: bad("Unknown property: ${operation.key}")
            when (operation.op) {
                PropertyOperationType.SET -> set[operation.key] = yamlValue(validateValue(definition, operation.value ?: bad("Value is required")))
                PropertyOperationType.REMOVE -> {
                    if (definition.required) bad("Required property cannot be removed: ${definition.key}")
                    remove += definition.key
                }
            }
        }
        val current = parsedFrontmatter(page.contentMd)
        defs.values.filter { it.required && it.key !in set && it.key !in remove && current.get(it.key) == null }
            .forEach { bad("Required property missing: ${it.key}") }
        return MarkdownFrontmatter.removeFields(MarkdownFrontmatter.updateFields(page.contentMd ?: "", set), remove)
    }

    @Transactional
    fun project(page: Page) {
        val defs = definitions.findAllByDeletedAtIsNullOrderByDisplayNameAsc()
        val root = runCatching { parsedFrontmatter(page.contentMd) }.getOrElse { return }
        values.deleteAllByPageId(requireNotNull(page.id))
        val hash = sha256(page.contentMd ?: "")
        defs.forEach { definition ->
            val raw = root.get(definition.key) ?: return@forEach
            val v = runCatching { validateValue(definition, raw) }.getOrNull() ?: return@forEach
            val row = PagePropertyValue(PagePropertyValueId(requireNotNull(page.id), requireNotNull(definition.id)), page, definition, hash, v)
            when (definition.type) {
                PropertyType.TEXT, PropertyType.URL, PropertyType.SELECT -> row.textValue = v.asText()
                PropertyType.NUMBER -> row.numberValue = v.decimalValue()
                PropertyType.BOOLEAN -> row.boolValue = v.booleanValue()
                PropertyType.DATE -> row.dateValue = LocalDate.parse(v.asText())
                PropertyType.DATETIME -> row.datetimeValue = Instant.parse(v.asText())
                PropertyType.PAGE_REF -> row.pageRef = pages.findBySlugAndDeletedAtIsNull(v.asText())
                else -> Unit
            }
            values.save(row)
        }
    }

    @Transactional
    fun reprojectAll() = pages.findAllByDeletedAtIsNull().forEach(::project)

    private fun active(id: java.util.UUID) = definitions.findById(id).orElseThrow { NotFoundException("Property not found") }.also { if (it.deletedAt != null) throw NotFoundException("Property not found") }
    private fun response(d: PropertyDefinition) = PropertyDefinitionResponse(requireNotNull(d.id), d.key, d.displayName, d.type, d.config, d.required, d.version, d.createdAt, d.updatedAt)
    private fun parsedFrontmatter(content: String?): JsonNode {
        val text = MarkdownFrontmatter.extractYamlInner(content ?: "") ?: return mapper.createObjectNode()
        return try { yaml.readTree(text) ?: mapper.createObjectNode() } catch (_: Exception) { bad("Malformed YAML frontmatter") }
    }
    private fun validateConfig(type: PropertyType, config: JsonNode): JsonNode {
        if (!config.isObject) bad("Property config must be an object")
        if (type == PropertyType.SELECT || type == PropertyType.MULTI_SELECT) {
            val options = config.get("options") ?: bad("Select property requires options")
            if (!options.isArray || options.any { !it.isTextual || it.asText().isBlank() } || options.map { it.asText() }.toSet().size != options.size()) bad("Select options must be unique non-empty strings")
        }
        return config
    }
    private fun removedOptions(old: JsonNode, next: JsonNode): Set<String> =
        old.path("options").map { it.asText() }.toSet() - next.path("options").map { it.asText() }.toSet()
    private fun validateValue(d: PropertyDefinition, value: JsonNode): JsonNode {
        val ok = when (d.type) {
            PropertyType.TEXT -> value.isTextual
            PropertyType.NUMBER -> value.isNumber || (value.isTextual && value.asText().toBigDecimalOrNull() != null)
            PropertyType.BOOLEAN -> value.isBoolean
            PropertyType.DATE -> value.isTextual && runCatching { LocalDate.parse(value.asText()) }.isSuccess
            PropertyType.DATETIME -> value.isTextual && runCatching { Instant.parse(value.asText()) }.isSuccess
            PropertyType.URL -> value.isTextual && runCatching { java.net.URI(value.asText()).toURL() }.isSuccess
            PropertyType.SELECT -> value.isTextual && value.asText() in d.config.path("options").map { it.asText() }
            PropertyType.MULTI_SELECT -> value.isArray && value.all { it.isTextual && it.asText() in d.config.path("options").map { o -> o.asText() } }
            PropertyType.PAGE_REF -> value.isTextual && pages.findBySlugAndDeletedAtIsNull(value.asText()) != null
        }
        if (!ok) bad("Invalid ${d.type} value for ${d.key}")
        return value
    }
    private fun yamlValue(value: JsonNode): String = mapper.writeValueAsString(value)
    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun bad(message: String): Nothing = throw UnprocessableEntityException(message)
}
