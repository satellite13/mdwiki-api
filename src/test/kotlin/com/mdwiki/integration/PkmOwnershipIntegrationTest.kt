package com.mdwiki.integration

import com.mdwiki.model.Folder
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class PkmOwnershipIntegrationTest {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var folders: FolderRepository

    @Test
    fun `two users can own isolated root Inbox folders`() {
        val suffix = UUID.randomUUID().toString()
        val alice = users.saveAndFlush(User(username = "alice-$suffix", email = "a-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))
        val bob = users.saveAndFlush(User(username = "bob-$suffix", email = "b-$suffix@test",
            passwordHash = "x", role = UserRole.EDITOR))
        val aliceInbox = folders.save(Folder(name = "Inbox", createdBy = alice, owner = alice))
        val bobInbox = folders.save(Folder(name = "Inbox", createdBy = bob, owner = bob))
        folders.flush()

        assertThat(aliceInbox.id).isNotEqualTo(bobInbox.id)
        assertThat(folders.findByOwnerIdAndParentIdIsNullAndName(alice.id!!, "Inbox")?.id)
            .isEqualTo(aliceInbox.id)
        assertThat(folders.findByOwnerIdAndParentIdIsNullAndName(bob.id!!, "Inbox")?.id)
            .isEqualTo(bobInbox.id)
    }
}
