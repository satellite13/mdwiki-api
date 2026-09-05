package com.mdwiki.service

import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Folder
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FolderAccessPolicyTest {
    @Mock lateinit var users: UserRepository

    @Test
    fun `bob cannot mutate alice folder while admin can`() {
        val alice = user("alice", UserRole.EDITOR)
        val bob = user("bob", UserRole.EDITOR)
        val admin = user("admin", UserRole.ADMIN)
        val folder = Folder(UUID.randomUUID(), "Inbox", owner = alice)
        whenever(users.findByUsername("bob")).thenReturn(bob)
        whenever(users.findByUsername("admin")).thenReturn(admin)
        val policy = FolderAccessPolicy(users)

        assertThrows<ForbiddenException> { policy.requireAccess(folder, "bob") }
        policy.requireAccess(folder, "admin")
    }

    @Test
    fun `owned folders cannot move outside same owner scope even for admin`() {
        val alice = user("alice", UserRole.EDITOR)
        val admin = user("admin", UserRole.ADMIN)
        whenever(users.findByUsername("admin")).thenReturn(admin)
        val root = Folder(UUID.randomUUID(), "Inbox", owner = alice)
        val owned = Folder(UUID.randomUUID(), "Child", parent = root, owner = alice)
        val shared = Folder(UUID.randomUUID(), "Shared")
        val policy = FolderAccessPolicy(users)

        assertThrows<ForbiddenException> { policy.requireMove(owned, null, "admin") }
        assertThrows<ForbiddenException> { policy.requireMove(owned, shared, "admin") }
    }

    @Test
    fun `shared folders cannot become descendants of owned roots`() {
        val alice = user("alice", UserRole.EDITOR)
        whenever(users.findByUsername("alice")).thenReturn(alice)
        val shared = Folder(UUID.randomUUID(), "Shared")
        val owned = Folder(UUID.randomUUID(), "Inbox", owner = alice)

        assertThrows<ForbiddenException> { FolderAccessPolicy(users).requireMove(shared, owned, "alice") }
    }

    @Test
    fun `deleting a shared ancestor cannot recursively delete another owners folder`() {
        val alice = user("alice", UserRole.EDITOR)
        val bob = user("bob", UserRole.EDITOR)
        whenever(users.findByUsername("bob")).thenReturn(bob)
        val shared = Folder(UUID.randomUUID(), "Shared")
        val aliceChild = Folder(UUID.randomUUID(), "Owned child", parent = shared, owner = alice)

        assertThrows<ForbiddenException> {
            FolderAccessPolicy(users).requireDeleteSubtree(listOf(shared, aliceChild), "bob")
        }
    }

    @Test
    fun `owned subtree pages cannot be moved into shared root on delete`() {
        val alice = user("alice", UserRole.EDITOR)
        whenever(users.findByUsername("alice")).thenReturn(alice)
        val owned = Folder(UUID.randomUUID(), "Inbox", owner = alice)

        assertThrows<ForbiddenException> {
            FolderAccessPolicy(users).requireDeleteSubtree(listOf(owned), "alice", movePagesToRoot = true)
        }
    }

    private fun user(name: String, role: UserRole) =
        User(UUID.randomUUID(), name, "$name@test", "x", role)
}
