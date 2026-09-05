package com.mdwiki.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.*
import com.mdwiki.service.FolderService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class FolderControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var folderService: FolderService

    private val sampleFolder = FolderResponse(
        id = UUID.randomUUID(),
        name = "Test Folder",
        parentId = null,
        sortOrder = 0,
        createdAt = Instant.now()
    )

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET tree returns folder tree`() {
        val tree = listOf(
            FolderTreeNode(id = "folder-${UUID.randomUUID()}", name = "docs", type = "folder"),
            FolderTreeNode(id = UUID.randomUUID().toString(), name = "Home", type = "page", slug = "home")
        )
        whenever(folderService.getTree("user")).thenReturn(tree)

        mockMvc.get("/api/folders/tree").andExpect {
            status { isOk() }
            jsonPath("$[0].type") { value("folder") }
            jsonPath("$[1].type") { value("page") }
        }
    }

    @Test
    @WithMockUser(username = "editor", roles = ["EDITOR"])
    fun `POST folders creates folder`() {
        whenever(folderService.create(any(), eq("editor"))).thenReturn(sampleFolder)

        mockMvc.post("/api/folders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFolderRequest("Test Folder"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Test Folder") }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `POST folders forbidden for READER`() {
        mockMvc.post("/api/folders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFolderRequest("Test"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `PUT folders renames folder`() {
        val folderId = sampleFolder.id
        whenever(folderService.rename(eq(folderId), any())).thenReturn(sampleFolder.copy(name = "Renamed"))

        mockMvc.put("/api/folders/$folderId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateFolderRequest("Renamed"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Renamed") }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `PUT folders move moves folder`() {
        val folderId = sampleFolder.id
        val newParentId = UUID.randomUUID()
        whenever(folderService.move(eq(folderId), any())).thenReturn(sampleFolder.copy(parentId = newParentId))

        mockMvc.put("/api/folders/$folderId/move") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MoveFolderRequest(parentId = newParentId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.parentId") { value(newParentId.toString()) }
        }
    }

    @Test
    @WithMockUser(roles = ["EDITOR"])
    fun `DELETE folders deletes folder`() {
        val folderId = sampleFolder.id

        mockMvc.delete("/api/folders/$folderId").andExpect {
            status { isOk() }
        }
    }
}
