package com.mdwiki.integration

import com.mdwiki.dto.SavedSearchWriteRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.model.SavedSearchMode
import com.mdwiki.model.SavedSearchSort
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.SavedSearchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class SavedSearchIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var service: SavedSearchService

    @Test
    fun `searches are private case-insensitive unique and versioned`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "saved-$suffix", email = "$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val other = users.saveAndFlush(User(username = "other-$suffix", email = "o-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val request = SavedSearchWriteRequest("Notes", "unicode λ", SavedSearchMode.HYBRID,
            listOf("pkm"), 0.5, SavedSearchSort.RELEVANCE)

        val created = service.create(owner.username, request)
        assertThat(service.list(owner.username)).extracting<String> { it.name }.containsExactly("Notes")
        assertThat(service.list(other.username)).isEmpty()
        assertThatThrownBy { service.create(owner.username, request.copy(name = "notes")) }
            .isInstanceOf(ConflictException::class.java)

        val updated = service.update(owner.username, created.id,
            request.copy(name = "Updated", expectedVersion = created.version))
        assertThat(updated.version).isEqualTo(2)
        assertThatThrownBy { service.update(owner.username, created.id,
            request.copy(expectedVersion = created.version)) }
            .isInstanceOf(ConflictException::class.java)
    }
}
