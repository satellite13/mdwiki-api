package com.mdwiki.mcp

import com.mdwiki.service.PageService
import com.mdwiki.service.usecase.DeletePageUseCase
import com.mdwiki.error.ForbiddenException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

@ExtendWith(MockitoExtension::class)
class WikiDeleteToolTest {
    @Mock lateinit var pages: PageService

    @AfterEach
    fun clearSecurity() = SecurityContextHolder.clearContext()

    @Test
    fun `owner actor is propagated for soft delete`() {
        authenticate("alice")

        WikiDeleteTool(pages).delete("owned", "SOFT")

        verify(pages).delete("owned", DeletePageUseCase.DeleteMode.SOFT, "alice")
    }

    @Test
    fun `admin actor is propagated for hard delete`() {
        authenticate("admin")

        WikiDeleteTool(pages).delete("owned", "HARD")

        verify(pages).delete("owned", DeletePageUseCase.DeleteMode.HARD, "admin")
    }

    @Test
    fun `foreign editor receives forbidden from page policy`() {
        authenticate("bob")
        whenever(pages.delete("owned", DeletePageUseCase.DeleteMode.SOFT, "bob"))
            .thenThrow(ForbiddenException("Folder belongs to another user"))

        assertThrows<ForbiddenException> {
            WikiDeleteTool(pages).delete("owned", "SOFT")
        }
    }

    private fun authenticate(username: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(username, "x")
    }
}
