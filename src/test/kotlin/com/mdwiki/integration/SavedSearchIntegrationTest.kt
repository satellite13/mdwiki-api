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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
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

    @Test
    fun `concurrent updates with one expected version yield one conflict`() {
        val suffix = UUID.randomUUID().toString()
        val owner = users.saveAndFlush(User(username = "race-$suffix", email = "race-$suffix@test",
            passwordHash = "x", role = UserRole.READER))
        val request = SavedSearchWriteRequest("Race", "q", SavedSearchMode.HYBRID)
        val created = service.create(owner.username, request)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = listOf("A", "B").map { name ->
            executor.submit<Boolean> {
                start.await()
                try {
                    service.update(owner.username, created.id,
                        request.copy(name = name, expectedVersion = created.version))
                    true
                } catch (_: ConflictException) {
                    false
                }
            }
        }
        start.countDown()
        val outcomes = futures.map { it.get(20, TimeUnit.SECONDS) }
        executor.shutdown()

        assertThat(outcomes).containsExactlyInAnyOrder(true, false)
        assertThat(service.get(owner.username, created.id).version).isEqualTo(2)
    }
}
