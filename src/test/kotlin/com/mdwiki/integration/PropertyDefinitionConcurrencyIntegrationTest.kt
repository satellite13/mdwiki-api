package com.mdwiki.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.PropertyDefinitionWriteRequest
import com.mdwiki.error.ConflictException
import com.mdwiki.model.PropertyType
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.PropertyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class PropertyDefinitionConcurrencyIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var properties: PropertyService
    @Autowired lateinit var mapper: ObjectMapper

    @Test
    fun `concurrent property definition updates with same version have one winner`() {
        val suffix = UUID.randomUUID().toString()
        val admin = users.saveAndFlush(User(
            username = "properties-$suffix",
            email = "$suffix@test",
            passwordHash = "x",
            role = UserRole.ADMIN
        ))
        val request = PropertyDefinitionWriteRequest("priority$suffix".take(20), "Priority", PropertyType.TEXT,
            emptyMap())
        val created = properties.create(request, admin.username)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val results = listOf("First", "Second").map { name ->
            executor.submit<Boolean> {
                start.await()
                try {
                    properties.update(created.id, request.copy(displayName = name, expectedVersion = created.version))
                    true
                } catch (_: ConflictException) {
                    false
                }
            }
        }
        start.countDown()
        val outcomes = results.map { it.get(20, TimeUnit.SECONDS) }
        executor.shutdown()

        assertThat(outcomes).containsExactlyInAnyOrder(true, false)
        assertThat(properties.listDefinitions().single { it.id == created.id }.version).isEqualTo(created.version + 1)
    }
}
