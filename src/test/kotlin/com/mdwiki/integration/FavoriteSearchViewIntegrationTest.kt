package com.mdwiki.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.SavedSearchWriteRequest
import com.mdwiki.dto.SavedViewWriteRequest
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.SavedSearchMode
import com.mdwiki.model.SavedViewType
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.SavedSearchService
import com.mdwiki.service.SavedViewService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class FavoriteSearchViewIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var searches: SavedSearchService
    @Autowired lateinit var views: SavedViewService
    @Autowired lateinit var mapper: ObjectMapper

    private fun emptyViewRequest(name: String, type: SavedViewType = SavedViewType.LIST) =
        SavedViewWriteRequest(
            name,
            type,
            mapper.createArrayNode(),
            mapper.createArrayNode(),
            null,
            mapper.createObjectNode()
        )

    @Test
    fun `add favorite search is idempotent and lists with favorited flag`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "fav-s-$suffix", email = "fav-s-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val created = searches.create(owner.username, SavedSearchWriteRequest(
            "Star me", "query", SavedSearchMode.HYBRID
        ))
        assertThat(created.favorited).isFalse()
        assertThat(searches.get(owner.username, created.id).favorited).isFalse()

        searches.addFavorite(created.id, owner.username)
        searches.addFavorite(created.id, owner.username)

        val listed = searches.listFavorites(owner.username)
        assertThat(listed).hasSize(1)
        assertThat(listed.single().id).isEqualTo(created.id)
        assertThat(listed.single().favorited).isTrue()
        assertThat(searches.list(owner.username).single().favorited).isTrue()
        assertThat(searches.get(owner.username, created.id).favorited).isTrue()

        searches.removeFavorite(created.id, owner.username)
        assertThat(searches.listFavorites(owner.username)).isEmpty()
        assertThat(searches.list(owner.username).single().favorited).isFalse()
    }

    @Test
    fun `cannot favorite another users search`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "own-s-$suffix", email = "own-s-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val other = users.saveAndFlush(User(username = "oth-s-$suffix", email = "oth-s-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val created = searches.create(owner.username, SavedSearchWriteRequest(
            "Private", "q", SavedSearchMode.TEXT
        ))

        assertThatThrownBy { searches.addFavorite(created.id, other.username) }
            .isInstanceOf(NotFoundException::class.java)
        assertThat(searches.listFavorites(other.username)).isEmpty()
    }

    @Test
    fun `add favorite view is idempotent and lists with favorited flag`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "fav-v-$suffix", email = "fav-v-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val created = views.create(emptyViewRequest("Star view"), owner.username)
        assertThat(created.favorited).isFalse()

        views.addFavorite(created.id, owner.username)
        views.addFavorite(created.id, owner.username)

        val listed = views.listFavorites(owner.username)
        assertThat(listed).hasSize(1)
        assertThat(listed.single().favorited).isTrue()
        assertThat(views.list(owner.username).single().favorited).isTrue()
        assertThat(views.get(created.id, owner.username).favorited).isTrue()

        views.removeFavorite(created.id, owner.username)
        assertThat(views.listFavorites(owner.username)).isEmpty()
        assertThat(views.list(owner.username).single().favorited).isFalse()
    }

    @Test
    fun `cannot favorite another users view`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "own-v-$suffix", email = "own-v-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val other = users.saveAndFlush(User(username = "oth-v-$suffix", email = "oth-v-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val created = views.create(emptyViewRequest("Secret", SavedViewType.TABLE), owner.username)

        assertThatThrownBy { views.addFavorite(created.id, other.username) }
            .isInstanceOf(NotFoundException::class.java)
        assertThat(views.listFavorites(other.username)).isEmpty()
    }
}
