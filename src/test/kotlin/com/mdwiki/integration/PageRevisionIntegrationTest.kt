package com.mdwiki.integration

import com.mdwiki.model.Page
import com.mdwiki.model.RevisionOperation
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.PageRevisionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.EntityManager
import java.util.UUID

@SpringBootTest
@Transactional
class PageRevisionIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var revisions: PageRevisionService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `records immutable ordered snapshots and preserves author name`() {
        val suffix = UUID.randomUUID().toString()
        val user = users.saveAndFlush(User(
            username = "revision-$suffix",
            email = "$suffix@test",
            passwordHash = "x",
            role = UserRole.EDITOR
        ))
        val page = pages.saveAndFlush(Page(slug = "revision-$suffix", title = "One", contentMd = "α\n"))

        revisions.record(page, user.username, RevisionOperation.CREATE)
        page.title = "Two"
        page.contentMd = "β"
        pages.saveAndFlush(page)
        revisions.record(page, user.username, RevisionOperation.EDIT)

        val listed = revisions.list(page, 20, null)
        assertThat(listed.map { it.revisionNo }).containsExactly(2, 1)
        assertThat(revisions.get(page, 1).contentMd).isEqualTo("α\n")
        assertThat(revisions.get(page, 2).contentHash).hasSize(64)

        entityManager.flush()
        entityManager.clear()
        jdbc.update("delete from users where id = ?", user.id)
        assertThat(revisions.get(page, 2).createdByName).isEqualTo(user.username)
    }

    @Test
    fun `skips identical edit revision without content or metadata diff`() {
        val suffix = UUID.randomUUID().toString()
        val user = users.saveAndFlush(User(
            username = "revision-noop-$suffix",
            email = "$suffix@noop",
            passwordHash = "x",
            role = UserRole.EDITOR
        ))
        val page = pages.saveAndFlush(Page(slug = "revision-noop-$suffix", title = "Same", contentMd = "body\n"))

        revisions.record(page, user.username, RevisionOperation.CREATE)
        val firstEdit = revisions.record(page, user.username, RevisionOperation.EDIT)
        val secondEdit = revisions.record(page, user.username, RevisionOperation.EDIT)

        assertThat(secondEdit.id).isEqualTo(firstEdit.id)
        assertThat(revisions.list(page, 20, null)).hasSize(1)
        assertThat(revisions.list(page, 20, null).single().revisionNo).isEqualTo(1)

        page.contentMd = "changed\n"
        pages.saveAndFlush(page)
        val changed = revisions.record(page, user.username, RevisionOperation.EDIT)
        assertThat(changed.revisionNo).isEqualTo(2)
        assertThat(revisions.list(page, 20, null)).hasSize(2)
    }
}
