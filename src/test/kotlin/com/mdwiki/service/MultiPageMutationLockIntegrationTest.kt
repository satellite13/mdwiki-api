package com.mdwiki.service

import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.transaction.annotation.Transactional
import java.sql.DriverManager

@SpringBootTest
class MultiPageMutationLockIntegrationTest {
    @Autowired private lateinit var pageRepository: PageRepository
    @Autowired private lateinit var environment: Environment

    @Test
    @Transactional
    fun `transaction advisory lock excludes a concurrent transaction`() {
        MultiPageMutationLock.acquire(pageRepository)

        val acquiredConcurrently = DriverManager.getConnection(
            environment.getRequiredProperty("spring.datasource.url"),
            environment.getRequiredProperty("spring.datasource.username"),
            environment.getRequiredProperty("spring.datasource.password")
        ).use { connection ->
            connection.prepareStatement("select pg_try_advisory_xact_lock(?)").use { statement ->
                statement.setLong(1, MultiPageMutationLock.KEY)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getBoolean(1)
                }
            }
        }

        assertFalse(acquiredConcurrently)
    }
}
