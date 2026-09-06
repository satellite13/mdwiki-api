package com.mdwiki.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.mdwiki.dto.SavedViewWriteRequest
import com.mdwiki.dto.ViewRunItem
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.model.PagePropertyValue
import com.mdwiki.model.PagePropertyValueId
import com.mdwiki.model.PropertyDefinition
import com.mdwiki.model.PropertyType
import com.mdwiki.model.SavedViewFilterMode
import com.mdwiki.model.SavedViewType
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PagePropertyValueRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.PropertyDefinitionRepository
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.SavedViewService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class SavedViewFilterModeIntegrationTest {
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var folders: FolderRepository
    @Autowired lateinit var pages: PageRepository
    @Autowired lateinit var definitions: PropertyDefinitionRepository
    @Autowired lateinit var values: PagePropertyValueRepository
    @Autowired lateinit var views: SavedViewService

    @Test
    fun `ALL intersects conditions while ANY unions them`() {
        val fixture = fixture()
        val filters = listOf(
            mapOf("key" to fixture.first.key, "op" to "EQ", "value" to true),
            mapOf("key" to fixture.second.key, "op" to "EQ", "value" to true),
        )

        val all = views.create(
            SavedViewWriteRequest(
                name = "All ${fixture.suffix}",
                type = SavedViewType.LIST,
                filters = filters,
            ),
            fixture.actor.username,
        )
        val any = views.create(
            SavedViewWriteRequest(
                name = "Any ${fixture.suffix}",
                type = SavedViewType.LIST,
                filterMode = SavedViewFilterMode.ANY,
                filters = filters,
            ),
            fixture.actor.username,
        )

        assertThat(pageIds(views.run(all.id, fixture.actor.username, null, 100)))
            .containsExactly(fixture.both.id)
        assertThat(pageIds(views.run(any.id, fixture.actor.username, null, 100)))
            .containsExactlyInAnyOrder(fixture.both.id, fixture.onlyFirst.id, fixture.onlySecond.id)
        assertThat(all.filterMode).isEqualTo(SavedViewFilterMode.ALL)
        assertThat(any.filterMode).isEqualTo(SavedViewFilterMode.ANY)
    }

    @Test
    fun `ANY and empty filters keep access and deletion predicates mandatory`() {
        val fixture = fixture()
        val outsider = users.saveAndFlush(User(
            username = "view-outsider-${fixture.suffix}",
            email = "view-outsider-${fixture.suffix}@test",
            passwordHash = "x",
            role = UserRole.EDITOR,
        ))
        val privateFolder = folders.saveAndFlush(Folder(
            name = "Private ${fixture.suffix}",
            createdBy = outsider,
            owner = outsider,
        ))
        val hidden = pages.saveAndFlush(Page(
            slug = "view-hidden-${fixture.suffix}",
            title = "Hidden",
            folder = privateFolder,
        ))
        val deleted = pages.saveAndFlush(Page(
            slug = "view-deleted-${fixture.suffix}",
            title = "Deleted",
            deletedAt = Instant.now(),
        ))
        setBoolean(hidden, fixture.first, true)
        setBoolean(deleted, fixture.first, true)

        val matchingAny = views.create(
            SavedViewWriteRequest(
                name = "Protected any ${fixture.suffix}",
                type = SavedViewType.LIST,
                filterMode = SavedViewFilterMode.ANY,
                filters = listOf(
                    mapOf("key" to fixture.first.key, "op" to "EQ", "value" to true),
                    mapOf("key" to fixture.second.key, "op" to "EQ", "value" to true),
                ),
            ),
            fixture.actor.username,
        )
        val emptyAny = views.create(
            SavedViewWriteRequest(
                name = "Empty any ${fixture.suffix}",
                type = SavedViewType.LIST,
                filterMode = SavedViewFilterMode.ANY,
            ),
            fixture.actor.username,
        )

        assertThat(pageIds(views.run(matchingAny.id, fixture.actor.username, null, 100)))
            .doesNotContain(hidden.id, deleted.id)
        assertThat(pageIds(views.run(emptyAny.id, fixture.actor.username, null, 100)))
            .contains(fixture.both.id, fixture.onlyFirst.id, fixture.onlySecond.id)
            .doesNotContain(hidden.id, deleted.id)
    }

    private fun fixture(): Fixture {
        val suffix = UUID.randomUUID().toString().take(8)
        val actor = users.saveAndFlush(User(
            username = "view-filter-$suffix",
            email = "view-filter-$suffix@test",
            passwordHash = "x",
            role = UserRole.EDITOR,
        ))
        val first = definitions.saveAndFlush(PropertyDefinition(
            key = "first-$suffix",
            displayName = "First",
            type = PropertyType.BOOLEAN,
            config = mapper.createObjectNode(),
            createdBy = actor,
        ))
        val second = definitions.saveAndFlush(PropertyDefinition(
            key = "second-$suffix",
            displayName = "Second",
            type = PropertyType.BOOLEAN,
            config = mapper.createObjectNode(),
            createdBy = actor,
        ))
        val both = pages.saveAndFlush(Page(slug = "view-both-$suffix", title = "Both"))
        val onlyFirst = pages.saveAndFlush(Page(slug = "view-first-$suffix", title = "First"))
        val onlySecond = pages.saveAndFlush(Page(slug = "view-second-$suffix", title = "Second"))
        setBoolean(both, first, true)
        setBoolean(both, second, true)
        setBoolean(onlyFirst, first, true)
        setBoolean(onlyFirst, second, false)
        setBoolean(onlySecond, first, false)
        setBoolean(onlySecond, second, true)
        return Fixture(suffix, actor, first, second, both, onlyFirst, onlySecond)
    }

    private fun setBoolean(page: Page, definition: PropertyDefinition, value: Boolean) {
        values.saveAndFlush(PagePropertyValue(
            id = PagePropertyValueId(page.id!!, definition.id!!),
            page = page,
            property = definition,
            sourceContentHash = "0".repeat(64),
            valueJson = mapper.nodeFactory.booleanNode(value),
            boolValue = value,
        ))
    }

    private fun pageIds(result: Map<String, Any?>): List<UUID> =
        (result["items"] as List<*>).map { (it as ViewRunItem).page.id }

    private data class Fixture(
        val suffix: String,
        val actor: User,
        val first: PropertyDefinition,
        val second: PropertyDefinition,
        val both: Page,
        val onlyFirst: Page,
        val onlySecond: Page,
    )
}
