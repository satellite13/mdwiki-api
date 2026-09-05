package com.mdwiki.integration

import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.service.PkmService
import com.mdwiki.service.PageService
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.error.NotFoundException
import com.mdwiki.dto.DailyNoteResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Callable
import java.util.concurrent.Future

@SpringBootTest(properties = ["spring.datasource.hikari.maximum-pool-size=4"])
class PkmDailyConcurrencyIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var service: PkmService
    @Autowired lateinit var pageService: PageService
    @Autowired lateinit var pages: PageRepository

    @Test
    fun `concurrent daily PUT creates one page`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "daily-$suffix"
        users.saveAndFlush(User(username = username, email = "$username@test", passwordHash = "x",
            role = UserRole.EDITOR))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures: List<Future<DailyNoteResponse>> = (1..2).map {
                pool.submit(Callable {
                    start.await()
                    service.putDaily(LocalDate.of(2026, 9, 5), username)
                })
            }
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertThat(results.map { it.page.id }.distinct()).hasSize(1)
            assertThat(results.count { it.created }).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `lossy-equivalent usernames get UUID based distinct daily slugs`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val first = users.saveAndFlush(User(username = "alice.foo-$suffix", email = "dot-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))
        val second = users.saveAndFlush(User(username = "alice_foo-$suffix", email = "underscore-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))

        val firstNote = service.putDaily(LocalDate.of(2026, 9, 6), first.username)
        val secondNote = service.putDaily(LocalDate.of(2026, 9, 6), second.username)

        assertThat(firstNote.page.slug).isNotEqualTo(secondNote.page.slug)
        assertThat(firstNote.page.slug).contains(first.id.toString())
        assertThat(secondNote.page.slug).contains(second.id.toString())
    }

    @Test
    fun `PUT restores a soft deleted mapped daily note while GET treats it missing`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val user = users.saveAndFlush(User(
            username = "daily-recover-$suffix",
            email = "recover-$suffix@test",
            passwordHash = "x",
            role = UserRole.EDITOR
        ))
        val date = LocalDate.of(2026, 9, 8)
        val created = service.putDaily(date, user.username)
        pageService.delete(created.page.slug, DeletePageUseCase.DeleteMode.SOFT, user.username)

        assertThrows<NotFoundException> { service.getDaily(date, user.username) }

        val restored = service.putDaily(date, user.username)
        assertThat(restored.page.id).isEqualTo(created.page.id)
        assertThat(pages.findById(restored.page.id).orElseThrow().deletedAt).isNull()
    }
}
