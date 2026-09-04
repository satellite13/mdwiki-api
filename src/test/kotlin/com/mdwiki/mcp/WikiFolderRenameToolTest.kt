package com.mdwiki.mcp

import com.mdwiki.dto.FolderResponse
import com.mdwiki.dto.UpdateFolderRequest
import com.mdwiki.service.FolderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WikiFolderRenameToolTest {
    @Mock private lateinit var folderService: FolderService

    @Test
    fun `renames folder and returns payload`() {
        val id = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        whenever(folderService.rename(eq(id), any())).thenReturn(
            FolderResponse(
                id = id,
                name = "New",
                parentId = parentId,
                sortOrder = 0,
                createdAt = Instant.parse("2026-09-04T06:00:00Z")
            )
        )

        val result = WikiFolderRenameTool(folderService).rename(id.toString(), "New")

        assertEquals(id.toString(), result["id"])
        assertEquals("New", result["name"])
        assertEquals(parentId.toString(), result["parentId"])
        assertEquals("renamed", result["status"])
        verify(folderService).rename(id, UpdateFolderRequest(name = "New"))
    }

    @Test
    fun `rejects invalid uuid`() {
        assertThrows<IllegalArgumentException> {
            WikiFolderRenameTool(folderService).rename("not-a-uuid", "New")
        }
    }
}
