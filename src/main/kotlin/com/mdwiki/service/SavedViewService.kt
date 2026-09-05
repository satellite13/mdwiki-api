package com.mdwiki.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.SavedViewResponse
import com.mdwiki.dto.SavedViewWriteRequest
import com.mdwiki.dto.ViewRunItem
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.UnprocessableEntityException
import com.mdwiki.mapper.toListItem
import com.mdwiki.model.PropertyDefinition
import com.mdwiki.model.PropertyType
import com.mdwiki.model.SavedView
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PropertyDefinitionRepository
import com.mdwiki.repository.SavedViewRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.util.PersistentInstant
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

@Service
class SavedViewService(
    private val views: SavedViewRepository,
    private val users: UserRepository,
    private val definitions: PropertyDefinitionRepository,
    private val pages: PageRepository,
    private val entityManager: EntityManager,
    private val mapper: ObjectMapper
) {
    @Transactional(readOnly = true)
    fun list(username: String) = views.findAllByUserIdOrderByUpdatedAtDesc(user(username).id!!).map(::response)

    @Transactional(readOnly = true)
    fun get(id: UUID, username: String) = response(owned(id, username))

    @Transactional
    fun create(request: SavedViewWriteRequest, username: String): SavedViewResponse {
        validate(request)
        val user = user(username)
        if (views.existsByUserIdAndNameIgnoreCase(user.id!!, request.name.trim())) throw ConflictException("View name already exists")
        return response(views.save(SavedView(user = user, name = request.name.trim(), type = request.type, filters = request.filters, sort = request.sort, grouping = request.grouping, layout = request.layout)))
    }

    @Transactional
    fun update(id: UUID, request: SavedViewWriteRequest, username: String): SavedViewResponse {
        validate(request)
        val view = owned(id, username)
        if (request.expectedVersion == null || request.expectedVersion != view.version) throw ConflictException("Saved view changed")
        if (!view.name.equals(request.name.trim(), true) && views.existsByUserIdAndNameIgnoreCase(view.user.id!!, request.name.trim())) throw ConflictException("View name already exists")
        view.name = request.name.trim(); view.type = request.type; view.filters = request.filters; view.sort = request.sort; view.grouping = request.grouping; view.layout = request.layout
        view.version++; view.updatedAt = PersistentInstant.now()
        return response(view)
    }

    @Transactional
    fun delete(id: UUID, username: String) = views.delete(owned(id, username))

    @Transactional(readOnly = true)
    fun run(id: UUID, username: String, cursor: String?, limit: Int): Map<String, Any?> {
        val view = owned(id, username)
        if (limit !in 1..100) bad("limit is invalid")
        val schema = activeSchema()
        validateAst(view.filters, view.sort, view.grouping, schema)
        val sort = view.sort.singleOrNull()
        val grouping = view.grouping?.takeIf { !it.isNull }?.path("key")?.asText()
        val cursorValue = cursor?.let { decodeCursor(it) }
        val actor = user(username)
        val parameters = linkedMapOf<String, Any>()
        val predicates = mutableListOf("p.deleted_at IS NULL")
        // A user may see shared-root pages and pages in own folders; administrators see all pages.
        predicates += "(${if (actor.role == UserRole.ADMIN) "true" else "(f.owner_id IS NULL OR f.owner_id = :actorId)"})"
        if (actor.role != UserRole.ADMIN) parameters["actorId"] = actor.id!!
        view.filters.forEachIndexed { index, node ->
            val definition = schema.getValue(node.path("key").asText())
            val alias = "v$index"
            val clause = filterClause(definition, node, alias, parameters)
            predicates += "EXISTS (SELECT 1 FROM page_property_values $alias WHERE $alias.page_id = p.id AND $alias.property_id = :${alias}Property AND $clause)"
            parameters["${alias}Property"] = definition.id!!
        }
        val sortDefinition = sort?.let { schema.getValue(it.path("key").asText()) }
        val sortAlias = if (sortDefinition == null) null else "sort_value"
        val sortExpression = sortDefinition?.let { projection(it, "$sortAlias.") }
        val joins = sortDefinition?.let { " LEFT JOIN page_property_values $sortAlias ON $sortAlias.page_id = p.id AND $sortAlias.property_id = :sortProperty " } ?: ""
        sortDefinition?.let { parameters["sortProperty"] = it.id!! }
        if (cursorValue != null) {
            if (sortDefinition == null) {
                predicates += "p.id > cast(:cursorId as uuid)"
            } else {
                val value = cursorValue.path("value")
                val direction = sort!!.path("direction").asText()
                val relation = if (direction == "ASC") ">" else "<"
                if (value.isNull) {
                    predicates += "($sortExpression IS NULL AND p.id > cast(:cursorId as uuid))"
                } else {
                    predicates += "($sortExpression $relation :cursorValue OR ($sortExpression = :cursorValue AND p.id > cast(:cursorId as uuid)) OR $sortExpression IS NULL)"
                    parameters["cursorValue"] = typedValue(sortDefinition, value)
                }
            }
            parameters["cursorId"] = cursorValue.path("id").asText()
        }
        val ordering = if (sortDefinition == null) "p.id ASC" else
            "$sortExpression ${sort!!.path("direction").asText()} NULLS LAST, p.id ASC"
        val sql = "SELECT p.* FROM pages p LEFT JOIN folders f ON f.id = p.folder_id $joins WHERE ${predicates.joinToString(" AND ")} ORDER BY $ordering LIMIT :queryLimit"
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(sql, com.mdwiki.model.Page::class.java).also { query ->
            parameters.forEach { (name, value) -> query.setParameter(name, value) }
            query.setParameter("queryLimit", limit + 1)
        }.resultList as List<com.mdwiki.model.Page>
        val hasNext = rows.size > limit
        val pageRows = rows.take(limit)
        val groupDefinition = grouping?.let(schema::get)
        val groupKeys = groupDefinition?.let { d ->
            pageRows.associate { page ->
                val key = entityManager.createNativeQuery(
                    "SELECT value_json FROM page_property_values WHERE page_id = :pageId AND property_id = :propertyId"
                ).also { q -> q.setParameter("pageId", page.id!!); q.setParameter("propertyId", d.id!!) }
                    .resultList.firstOrNull()?.toString()
                page.id!! to key
            }
        }.orEmpty()
        val items = pageRows.map { page -> ViewRunItem(page.toListItem(), groupKeys[page.id!!]) }
        val next = if (!hasNext || pageRows.isEmpty()) null else encodeCursor(
            pageRows.last().id!!,
            sortDefinition?.let { d -> valueForCursor(d, pageRows.last().id!!) }
        )
        return mapOf("items" to items, "nextCursor" to next, "view" to response(view), "nullOrdering" to "NULLS_LAST")
    }

    private fun validate(request: SavedViewWriteRequest) {
        if (request.name.trim().length !in 1..120 || !request.filters.isArray || !request.sort.isArray || !request.layout.isObject || (request.grouping != null && !request.grouping.isObject)) bad("Invalid saved view JSON AST")
        validateAst(request.filters, request.sort, request.grouping, activeSchema())
    }
    private fun activeSchema() = definitions.findAllByDeletedAtIsNullOrderByDisplayNameAsc().associateBy { it.key }
    private fun validateAst(filters: JsonNode, sort: JsonNode, grouping: JsonNode?, schema: Map<String, PropertyDefinition>) {
        if (sort.size() > 1) bad("Only one sort property is supported")
        filters.forEach { f ->
            if (!f.isObject || !f.path("key").isTextual || !f.path("op").isTextual) bad("Invalid filter")
            val d = schema[f.path("key").asText()] ?: bad("Unknown or deleted property: ${f.path("key").asText()}")
            val op = f.path("op").asText()
            val permitted = when (d.type) {
                PropertyType.TEXT, PropertyType.URL -> setOf("EQ", "NEQ", "CONTAINS", "EXISTS")
                PropertyType.NUMBER, PropertyType.DATE, PropertyType.DATETIME -> setOf("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "EXISTS")
                PropertyType.BOOLEAN -> setOf("EQ", "NEQ", "EXISTS")
                PropertyType.SELECT -> setOf("EQ", "NEQ", "CONTAINS", "EXISTS")
                PropertyType.MULTI_SELECT -> setOf("CONTAINS", "EXISTS")
                PropertyType.PAGE_REF -> setOf("EQ", "NEQ", "EXISTS")
            }
            if (op !in permitted || (op != "EXISTS" && !f.has("value"))) bad("Invalid operator or value for ${d.key}")
            if (op != "EXISTS") typedValue(d, f.path("value"))
        }
        sort.forEach { s ->
            if (!s.isObject || !s.path("key").isTextual || s.path("direction").asText() !in setOf("ASC", "DESC") || schema[s.path("key").asText()] == null) bad("Invalid sort")
        }
        if (grouping != null && !grouping.isNull && (!grouping.path("key").isTextual || schema[grouping.path("key").asText()] == null)) bad("Invalid grouping")
    }
    private fun filterClause(d: PropertyDefinition, f: JsonNode, alias: String, parameters: MutableMap<String, Any>): String {
        val op = f.path("op").asText()
        if (op == "EXISTS") return "1=1"
        val valueName = "${alias}Value"
        parameters[valueName] = typedValue(d, f.path("value"))
        if (d.type == PropertyType.MULTI_SELECT) return "$alias.value_json ? :$valueName"
        val column = projection(d, "$alias.")
        return when (op) {
            "EQ" -> "$column = :$valueName"
            "NEQ" -> "$column <> :$valueName"
            "CONTAINS" -> "$column ILIKE ('%' || :$valueName || '%')"
            "GT" -> "$column > :$valueName"
            "GTE" -> "$column >= :$valueName"
            "LT" -> "$column < :$valueName"
            "LTE" -> "$column <= :$valueName"
            else -> bad("Invalid operator")
        }
    }
    private fun projection(d: PropertyDefinition, prefix: String) = when (d.type) {
        PropertyType.NUMBER -> "${prefix}number_value"
        PropertyType.BOOLEAN -> "${prefix}bool_value"
        PropertyType.DATE -> "${prefix}date_value"
        PropertyType.DATETIME -> "${prefix}datetime_value"
        PropertyType.PAGE_REF -> "${prefix}page_ref_id"
        else -> "${prefix}text_value"
    }
    private fun typedValue(d: PropertyDefinition, value: JsonNode): Any = try {
        when (d.type) {
            PropertyType.NUMBER -> BigDecimal(value.asText())
            PropertyType.BOOLEAN -> if (value.isBoolean) value.booleanValue() else throw IllegalArgumentException()
            PropertyType.DATE -> LocalDate.parse(value.asText())
            PropertyType.DATETIME -> Instant.parse(value.asText())
            PropertyType.PAGE_REF -> runCatching { UUID.fromString(value.asText()) }.getOrElse {
                pages.findActiveIdBySlug(value.asText()) ?: throw IllegalArgumentException()
            }
            else -> value.asText().also { if (it.isBlank()) throw IllegalArgumentException() }
        }
    } catch (_: Exception) { bad("Invalid value for ${d.key}") }
    private fun encodeCursor(id: UUID, value: Any?): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        mapper.createObjectNode().put("id", id.toString()).also { node ->
            if (value == null) node.putNull("value") else node.set<JsonNode>("value", mapper.valueToTree(value))
        }.toString().toByteArray()
    )
    private fun decodeCursor(cursor: String): JsonNode = try {
        mapper.readTree(String(Base64.getUrlDecoder().decode(cursor))).also {
            if (!it.isObject || !it.path("id").isTextual || (!it.has("value"))) bad("Invalid cursor")
            UUID.fromString(it.path("id").asText())
        }
    } catch (_: Exception) { bad("Invalid cursor") }
    private fun valueForCursor(d: PropertyDefinition, pageId: UUID): Any? {
        val column = projection(d, "v.")
        return entityManager.createNativeQuery("SELECT $column FROM page_property_values v WHERE v.page_id = :pageId AND v.property_id = :propertyId")
            .also { it.setParameter("pageId", pageId); it.setParameter("propertyId", d.id!!) }.resultList.firstOrNull()
    }
    private fun user(username: String) = users.findByUsername(username) ?: throw NotFoundException("User not found")
    private fun owned(id: UUID, username: String) = views.findByIdAndUserId(id, user(username).id!!) ?: throw NotFoundException("Saved view not found")
    private fun response(v: SavedView) = SavedViewResponse(v.id!!, v.name, v.type, v.filters, v.sort, v.grouping, v.layout, v.version, v.createdAt, v.updatedAt)
    private fun bad(message: String): Nothing = throw UnprocessableEntityException(message)
}
