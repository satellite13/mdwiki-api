package com.mdwiki.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.model.Page
import com.mdwiki.model.PagePropertyValue
import com.mdwiki.model.PagePropertyValueId
import com.mdwiki.model.PropertyDefinition
import com.mdwiki.model.PropertyType
import com.mdwiki.model.SavedView
import com.mdwiki.model.SavedViewType
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.PagePropertyValueRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PropertyDefinitionRepository
import com.mdwiki.repository.SavedViewRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.SavedViewService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class SavedViewCursorIntegrationTest {
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var definitions: PropertyDefinitionRepository
    @Autowired lateinit var values: PagePropertyValueRepository
    @Autowired lateinit var views: SavedViewRepository
    @Autowired lateinit var service: SavedViewService

    @Test
    fun `boolean sort cursor loads the second page`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val user = users.saveAndFlush(User(username = "view-$suffix", email = "view-$suffix@test", passwordHash = "x", role = UserRole.EDITOR))
        val definition = definitions.saveAndFlush(PropertyDefinition(
            key = "done-$suffix",
            displayName = "Done",
            type = PropertyType.BOOLEAN,
            config = mapper.createObjectNode(),
            createdBy = user
        ))
        val firstPage = pages.saveAndFlush(Page(slug = "view-first-$suffix", title = "First"))
        val secondPage = pages.saveAndFlush(Page(slug = "view-second-$suffix", title = "Second"))
        listOf(firstPage, secondPage).forEach { page ->
            values.saveAndFlush(PagePropertyValue(PagePropertyValueId(page.id!!, definition.id!!), page, definition, "0".repeat(64), mapper.nodeFactory.booleanNode(true), boolValue = true))
        }
        val view = views.saveAndFlush(SavedView(
            user = user,
            name = "Boolean $suffix",
            type = SavedViewType.LIST,
            filters = mapper.readTree("""[{"key":"${definition.key}","op":"EXISTS"}]"""),
            sort = mapper.readTree("""[{"key":"${definition.key}","direction":"ASC"}]"""),
            layout = mapper.createObjectNode()
        ))

        val pageOne = service.run(view.id!!, user.username, null, 1)
        val cursor = pageOne["nextCursor"] as String
        val pageTwo = service.run(view.id!!, user.username, cursor, 1)

        val firstId = (pageOne["items"] as List<*>).single().let { it as com.mdwiki.dto.ViewRunItem }.page.id
        val secondId = (pageTwo["items"] as List<*>).single().let { it as com.mdwiki.dto.ViewRunItem }.page.id
        assertThat(setOf(firstId, secondId)).containsExactlyInAnyOrder(firstPage.id, secondPage.id)
        assertThat(pageTwo["nextCursor"]).isNull()
    }
}
