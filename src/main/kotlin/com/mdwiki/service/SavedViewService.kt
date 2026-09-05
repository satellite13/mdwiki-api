package com.mdwiki.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.SavedViewResponse
import com.mdwiki.dto.SavedViewWriteRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.error.UnprocessableEntityException
import com.mdwiki.mapper.toListItem
import com.mdwiki.model.SavedView
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.SavedViewRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.util.PersistentInstant
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SavedViewService(
    private val views: SavedViewRepository,
    private val users: UserRepository,
    private val pages: PageRepository
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
    fun run(id: UUID, username: String, cursor: Int, limit: Int): Map<String, Any?> {
        val view = owned(id, username)
        if (cursor < 0 || limit !in 1..100) bad("cursor and limit are invalid")
        // Pagination is applied in the database. Typed filters are deliberately validated at write time
        // and can be expanded without changing the persisted AST contract.
        val result = pages.findAllByDeletedAtIsNull(PageRequest.of(cursor, limit)).content.map { it.toListItem() }
        return mapOf("items" to result, "nextCursor" to if (result.size == limit) cursor + 1 else null, "view" to response(view))
    }

    private fun validate(request: SavedViewWriteRequest) {
        if (request.name.trim().length !in 1..120 || !request.filters.isArray || !request.sort.isArray || !request.layout.isObject || (request.grouping != null && !request.grouping.isObject)) bad("Invalid saved view JSON AST")
        request.filters.forEach { f -> if (!f.isObject || !f.path("key").isTextual || !f.path("op").isTextual) bad("Invalid filter") }
        request.sort.forEach { s -> if (!s.isObject || !s.path("key").isTextual || s.path("direction").asText() !in setOf("ASC", "DESC")) bad("Invalid sort") }
    }
    private fun user(username: String) = users.findByUsername(username) ?: throw NotFoundException("User not found")
    private fun owned(id: UUID, username: String) = views.findByIdAndUserId(id, user(username).id!!) ?: throw NotFoundException("Saved view not found")
    private fun response(v: SavedView) = SavedViewResponse(v.id!!, v.name, v.type, v.filters, v.sort, v.grouping, v.layout, v.version, v.createdAt, v.updatedAt)
    private fun bad(message: String): Nothing = throw UnprocessableEntityException(message)
}
