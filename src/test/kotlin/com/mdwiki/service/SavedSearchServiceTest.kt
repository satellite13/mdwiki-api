package com.mdwiki.service

import com.mdwiki.dto.SavedSearchWriteRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.model.SavedSearch
import com.mdwiki.model.SavedSearchMode
import com.mdwiki.model.User
import com.mdwiki.repository.SavedSearchRepository
import com.mdwiki.repository.UserFavoriteSearchRepository
import com.mdwiki.repository.UserRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SavedSearchServiceTest {
    @Mock lateinit var searches: SavedSearchRepository
    @Mock lateinit var users: UserRepository
    @Mock lateinit var favoriteSearches: UserFavoriteSearchRepository

    @Test
    fun `update translates unique constraint race to conflict`() {
        val user = User(id = UUID.randomUUID(), username = "reader", email = "r@test", passwordHash = "x")
        val saved = SavedSearch(id = UUID.randomUUID(), user = user, name = "Old",
            queryText = "q", mode = SavedSearchMode.HYBRID, version = 3)
        whenever(users.findByUsername("reader")).thenReturn(user)
        whenever(searches.findByIdAndUserIdForUpdate(saved.id!!, user.id!!)).thenReturn(saved)
        whenever(searches.findAllByUserIdOrderByUpdatedAtDesc(user.id!!)).thenReturn(listOf(saved))
        whenever(searches.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("uq_saved_search_user_lower_name"))

        assertThatThrownBy {
            SavedSearchService(searches, users, favoriteSearches).update("reader", saved.id!!,
                SavedSearchWriteRequest("Race", "q", SavedSearchMode.HYBRID, expectedVersion = 3))
        }.isInstanceOf(ConflictException::class.java)
            .hasMessage("Saved search name already exists")
    }
}
