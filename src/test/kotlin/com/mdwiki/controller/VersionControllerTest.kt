package com.mdwiki.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class VersionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET version is public and returns build info`() {
        mockMvc.get("/api/version").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("mdwiki-api") }
            jsonPath("$.version") { value("0.1.8") }
            jsonPath("$.versionTag") { exists() }
            jsonPath("$.gitSha") { exists() }
        }
    }
}
