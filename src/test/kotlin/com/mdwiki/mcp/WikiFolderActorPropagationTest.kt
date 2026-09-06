package com.mdwiki.mcp

import com.mdwiki.dto.FolderResponse
import com.mdwiki.dto.MoveFolderRequest
import com.mdwiki.service.FolderService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WikiFolderActorPropagationTest {
    @Mock lateinit var folders: FolderService

    @BeforeEach
    fun authenticate() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("bob", "x")
    }

    @AfterEach
    fun clearSecurity() = SecurityContextHolder.clearContext()

    @Test
    fun `move tool propagates authenticated actor to ACL service`() {
        val folderId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        whenever(folders.move(eq(folderId), any(), eq("bob"))).thenReturn(
            FolderResponse(folderId, "Folder", parentId, 0, Instant.now())
        )

        WikiFolderMoveTool(folders).move(folderId.toString(), parentId.toString(), false)

        verify(folders).move(folderId, MoveFolderRequest(parentId), "bob")
    }

    @Test
    fun `delete tool propagates authenticated actor to ACL service`() {
        val folderId = UUID.randomUUID()

        WikiFolderDeleteTool(folders).delete(folderId.toString(), "DELETE")

        verify(folders).delete(folderId, "bob")
    }
}
