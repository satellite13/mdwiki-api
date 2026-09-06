package com.mdwiki.mcp

import com.mdwiki.error.ForbiddenException
import com.mdwiki.service.AnnotationService
import com.mdwiki.service.AttachmentService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WikiAdjacentOwnershipTest {
    @Mock lateinit var annotations: AnnotationService
    @Mock lateinit var attachments: AttachmentService

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `owner actor reaches annotation mutation policy`() {
        val id = UUID.randomUUID()
        authenticate("alice")

        WikiAnnotationDeleteTool(annotations).delete(id.toString())

        verify(annotations).delete(id, "alice")
    }

    @Test
    fun `foreign editor receives annotation ownership forbidden`() {
        val id = UUID.randomUUID()
        authenticate("bob")
        whenever(annotations.delete(id, "bob")).thenThrow(ForbiddenException("Folder belongs to another user"))

        assertThrows<ForbiddenException> { WikiAnnotationDeleteTool(annotations).delete(id.toString()) }
    }

    @Test
    fun `admin actor reaches attachment mutation policy`() {
        val id = UUID.randomUUID()
        authenticate("admin")

        WikiAttachmentDeleteTool(attachments).delete(id.toString())

        verify(attachments).delete(id, "admin")
    }

    private fun authenticate(username: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(username, "x")
    }
}
