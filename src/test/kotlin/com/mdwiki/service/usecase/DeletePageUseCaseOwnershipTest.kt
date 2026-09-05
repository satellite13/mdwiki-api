package com.mdwiki.service.usecase

import com.mdwiki.error.ForbiddenException
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.rag.RagService
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.FolderAccessPolicy
import com.mdwiki.service.FrontmatterMetaService
import com.mdwiki.service.PageMetadataService
import com.mdwiki.service.SyncService
import com.mdwiki.service.WikiFileService
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doReturn
import org.mockito.Mockito.mockingDetails
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DeletePageUseCaseOwnershipTest {
    @Mock lateinit var pages: PageRepository
    @Mock lateinit var metadata: PageMetadataService
    @Mock lateinit var rag: RagService
    @Mock lateinit var files: WikiFileService
    @Mock lateinit var sync: SyncService
    @Mock lateinit var users: UserRepository

    @ParameterizedTest
    @EnumSource(DeletePageUseCase.DeleteMode::class)
    fun `owner can delete owned page in both modes`(mode: DeletePageUseCase.DeleteMode) {
        val alice = user("alice", UserRole.EDITOR)
        val page = ownedPage(alice, "owner-${mode.name.lowercase()}")
        whenever(users.findByUsername("alice")).thenReturn(alice)
        whenever(pages.findBySlugForUpdate(page.slug)).thenReturn(page)
        if (mode == DeletePageUseCase.DeleteMode.SOFT) doReturn(page).whenever(pages).save(page)

        useCase().execute(page.slug, mode, "alice")

        if (mode == DeletePageUseCase.DeleteMode.SOFT) {
            assertNotNull(page.deletedAt)
            assertEquals(1, saveInvocationCount())
        } else {
            verify(pages).delete(page)
        }
    }

    @ParameterizedTest
    @EnumSource(DeletePageUseCase.DeleteMode::class)
    fun `foreign editor gets forbidden in both modes`(mode: DeletePageUseCase.DeleteMode) {
        val alice = user("alice", UserRole.EDITOR)
        val bob = user("bob", UserRole.EDITOR)
        val page = ownedPage(alice, "foreign-${mode.name.lowercase()}")
        whenever(users.findByUsername("bob")).thenReturn(bob)
        whenever(pages.findBySlugForUpdate(page.slug)).thenReturn(page)

        assertThrows<ForbiddenException> { useCase().execute(page.slug, mode, "bob") }
    }

    @ParameterizedTest
    @EnumSource(DeletePageUseCase.DeleteMode::class)
    fun `admin can delete another owners page in both modes`(mode: DeletePageUseCase.DeleteMode) {
        val alice = user("alice", UserRole.EDITOR)
        val admin = user("admin", UserRole.ADMIN)
        val page = ownedPage(alice, "admin-${mode.name.lowercase()}")
        whenever(users.findByUsername("admin")).thenReturn(admin)
        whenever(pages.findBySlugForUpdate(page.slug)).thenReturn(page)
        if (mode == DeletePageUseCase.DeleteMode.SOFT) doReturn(page).whenever(pages).save(page)

        useCase().execute(page.slug, mode, "admin")

        if (mode == DeletePageUseCase.DeleteMode.SOFT) assertEquals(1, saveInvocationCount())
        else verify(pages).delete(page)
    }

    private fun useCase() = DeletePageUseCase(
        pages,
        metadata,
        rag,
        files,
        sync,
        FrontmatterMetaService(),
        FolderAccessPolicy(users)
    )

    private fun saveInvocationCount() =
        mockingDetails(pages).invocations.count { it.method.name == "save" }

    private fun ownedPage(owner: User, slug: String) = Page(
        UUID.randomUUID(),
        slug,
        slug,
        contentMd = "",
        folder = Folder(UUID.randomUUID(), "Inbox", owner = owner)
    )

    private fun user(username: String, role: UserRole) =
        User(UUID.randomUUID(), username, "$username@test", "x", role)
}
