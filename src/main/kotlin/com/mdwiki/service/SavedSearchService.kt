package com.mdwiki.service

import com.mdwiki.dto.SavedSearchResponse
import com.mdwiki.dto.SavedSearchWriteRequest
import com.mdwiki.error.BadRequestException
import com.mdwiki.error.ConflictException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.SavedSearch
import com.mdwiki.repository.SavedSearchRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.util.PersistentInstant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SavedSearchService(
    private val searches: SavedSearchRepository,
    private val users: UserRepository
) {
    @Transactional(readOnly = true)
    fun list(username: String) = searches.findAllByUserIdOrderByUpdatedAtDesc(userId(username)).map(::response)

    @Transactional(readOnly = true)
    fun get(username: String, id: UUID) = response(owned(username, id))

    @Transactional
    fun create(username: String, request: SavedSearchWriteRequest): SavedSearchResponse {
        validate(request)
        val user = users.findByUsername(username) ?: throw NotFoundException("User not found")
        if (searches.existsByUserIdAndNameIgnoreCase(requireNotNull(user.id), request.name.trim())) {
            throw ConflictException("Saved search name already exists")
        }
        return try {
            response(searches.saveAndFlush(SavedSearch(
                user = user,
                name = request.name.trim(),
                queryText = request.queryText.trim(),
                mode = request.mode,
                tags = request.tags.distinct(),
                minScore = request.minScore,
                sort = request.sort
            )))
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("Saved search name already exists")
        }
    }

    @Transactional
    fun update(username: String, id: UUID, request: SavedSearchWriteRequest): SavedSearchResponse {
        validate(request)
        val search = owned(username, id)
        if (request.expectedVersion == null || request.expectedVersion != search.version) {
            throw ConflictException("Saved search has changed")
        }
        val duplicate = searches.findAllByUserIdOrderByUpdatedAtDesc(requireNotNull(search.user.id))
            .any { it.id != search.id && it.name.equals(request.name.trim(), ignoreCase = true) }
        if (duplicate) throw ConflictException("Saved search name already exists")
        search.name = request.name.trim()
        search.queryText = request.queryText.trim()
        search.mode = request.mode
        search.tags = request.tags.distinct()
        search.minScore = request.minScore
        search.sort = request.sort
        search.version++
        search.updatedAt = PersistentInstant.now()
        return response(searches.saveAndFlush(search))
    }

    @Transactional
    fun delete(username: String, id: UUID) {
        val user = users.findByUsername(username) ?: return
        searches.findByIdAndUserId(id, requireNotNull(user.id))?.let(searches::delete)
    }

    private fun validate(request: SavedSearchWriteRequest) {
        if (request.name.trim().length !in 1..120) throw BadRequestException("name must be 1..120 characters")
        if (request.queryText.trim().length !in 1..1000) throw BadRequestException("queryText must be 1..1000 characters")
        if (request.minScore != null && request.minScore !in 0.0..1.0) throw BadRequestException("minScore must be 0..1")
    }

    private fun userId(username: String) = requireNotNull(
        users.findByUsername(username)?.id ?: throw NotFoundException("User not found")
    )

    private fun owned(username: String, id: UUID) =
        searches.findByIdAndUserId(id, userId(username)) ?: throw NotFoundException("Saved search not found")

    private fun response(s: SavedSearch) = SavedSearchResponse(
        requireNotNull(s.id), s.name, s.queryText, s.mode, s.tags, s.minScore,
        s.sort, s.version, s.createdAt, s.updatedAt
    )
}
