package com.mdwiki.controller

import com.mdwiki.dto.GraphNode
import com.mdwiki.dto.GraphResponse
import com.mdwiki.service.GraphService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class GraphControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var graphService: GraphService

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET wiki graph delegates to graphService`() {
        val graph = GraphResponse(
            nodes = listOf(GraphNode("x", "X", emptyList(), false)),
            edges = emptyList()
        )
        whenever(graphService.getFullWikiGraph(null)).thenReturn(graph)

        mockMvc.get("/api/graph/wiki").andExpect {
            status { isOk() }
            jsonPath("$.nodes[0].slug") { value("x") }
        }
        verify(graphService).getFullWikiGraph(null)
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `GET wiki graph passes highlight query`() {
        whenever(graphService.getFullWikiGraph("home")).thenReturn(GraphResponse(emptyList(), emptyList()))

        mockMvc.get("/api/graph/wiki") {
            param("highlight", "home")
        }.andExpect {
            status { isOk() }
        }
        verify(graphService).getFullWikiGraph("home")
    }
}
