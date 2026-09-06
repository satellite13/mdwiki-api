package com.mdwiki.controller

import com.mdwiki.error.NotFoundException
import com.mdwiki.service.PkmService
import com.mdwiki.service.SavedSearchService
import com.mdwiki.service.SavedViewService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteSearchViewControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var pkmService: PkmService
    @MockitoBean lateinit var savedSearches: SavedSearchService
    @MockitoBean lateinit var savedViews: SavedViewService

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader can put delete and list favorite searches`() {
        val id = UUID.randomUUID()
        whenever(savedSearches.listFavorites("reader")).thenReturn(emptyList())

        mockMvc.put("/api/me/favorite-searches/$id").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/me/favorite-searches/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/me/favorite-searches").andExpect { status { isOk() } }

        verify(savedSearches).addFavorite(id, "reader")
        verify(savedSearches).removeFavorite(id, "reader")
        verify(savedSearches).listFavorites("reader")
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `favorite search of another user returns 404`() {
        val id = UUID.randomUUID()
        whenever(savedSearches.addFavorite(eq(id), eq("reader")))
            .thenThrow(NotFoundException("Saved search not found"))

        mockMvc.put("/api/me/favorite-searches/$id").andExpect { status { isNotFound() } }
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `reader can put delete and list favorite views`() {
        val id = UUID.randomUUID()
        whenever(savedViews.listFavorites("reader")).thenReturn(emptyList())

        mockMvc.put("/api/me/favorite-views/$id").andExpect { status { isNoContent() } }
        mockMvc.delete("/api/me/favorite-views/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/me/favorite-views").andExpect { status { isOk() } }

        verify(savedViews).addFavorite(id, "reader")
        verify(savedViews).removeFavorite(id, "reader")
        verify(savedViews).listFavorites("reader")
    }

    @Test
    @WithMockUser(username = "reader", roles = ["READER"])
    fun `favorite view of another user returns 404`() {
        val id = UUID.randomUUID()
        whenever(savedViews.addFavorite(eq(id), any()))
            .thenThrow(NotFoundException("Saved view not found"))

        mockMvc.put("/api/me/favorite-views/$id").andExpect { status { isNotFound() } }
    }
}
