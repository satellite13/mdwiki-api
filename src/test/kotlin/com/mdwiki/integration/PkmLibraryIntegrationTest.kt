package com.mdwiki.integration

import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserFavoritePageRepository
import com.mdwiki.repository.UserRecentPageRepository
import com.mdwiki.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class PkmLibraryIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var recent: UserRecentPageRepository
    @Autowired lateinit var favorites: UserFavoritePageRepository
    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `recent increments favorite is idempotent and deleted pages are filtered`() {
        val suffix = UUID.randomUUID().toString()
        val user = users.saveAndFlush(User(username = "library-$suffix", email = "$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val other = users.saveAndFlush(User(username = "other-$suffix", email = "other-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val page = pages.saveAndFlush(Page(slug = "library-$suffix", title = "Library", contentMd = ""))
        val otherPage = pages.saveAndFlush(Page(slug = "other-$suffix", title = "Other", contentMd = ""))

        recent.touch(user.id!!, page.id!!)
        recent.touch(user.id!!, page.id!!)
        favorites.add(user.id!!, page.id!!)
        favorites.add(user.id!!, page.id!!)
        recent.touch(other.id!!, otherPage.id!!)
        favorites.add(other.id!!, otherPage.id!!)
        entityManager.flush()
        entityManager.clear()

        assertThat(recent.listActive(user.id!!).single().openCount).isEqualTo(2)
        assertThat(favorites.listActive(user.id!!)).hasSize(1)
        assertThat(recent.listActive(user.id!!).map { it.pageId }).containsExactly(page.id)
        assertThat(favorites.listActive(user.id!!).map { it.pageId }).containsExactly(page.id)

        val persisted = pages.findById(page.id!!).orElseThrow()
        persisted.deletedAt = Instant.now()
        pages.saveAndFlush(persisted)
        entityManager.clear()
        assertThat(recent.listActive(user.id!!)).isEmpty()
        assertThat(favorites.listActive(user.id!!)).isEmpty()
    }
}
