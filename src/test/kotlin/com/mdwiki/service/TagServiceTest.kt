package com.mdwiki.service

import com.mdwiki.model.Tag
import com.mdwiki.repository.TagRepository
import com.mdwiki.repository.TagWithPageCountView
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TagServiceTest {

    @Mock
    private lateinit var tagRepository: TagRepository

    private lateinit var tagService: TagService

    @BeforeEach
    fun setUp() {
        tagService = TagService(tagRepository)
    }

    @Test
    fun `getOrCreateTags creates missing tags`() {
        val existingTag = Tag(id = UUID.randomUUID(), name = "kotlin")
        whenever(tagRepository.findByNameIn(setOf("kotlin", "spring"))).thenReturn(listOf(existingTag))
        whenever(tagRepository.save(any<Tag>())).thenAnswer { it.arguments[0] }

        val tags = tagService.getOrCreateTags(setOf("kotlin", "spring"))

        assertEquals(2, tags.size)
        verify(tagRepository).save(argThat<Tag> { name == "spring" })
    }

    @Test
    fun `cleanupOrphanedTags deletes unused tags`() {
        val orphan = Tag(id = UUID.randomUUID(), name = "unused")
        whenever(tagRepository.findOrphanedTags()).thenReturn(listOf(orphan))

        tagService.cleanupOrphanedTags()

        verify(tagRepository).deleteAll(listOf(orphan))
    }

    @Test
    fun `findAll returns tags with usage count`() {
        val id = UUID.randomUUID()
        whenever(tagRepository.findAllWithPageCount()).thenReturn(
            listOf(TestTagWithPageCountView(id = id, name = "kotlin", pageCount = 4))
        )

        val result = tagService.findAll()

        assertEquals(1, result.size)
        assertEquals(id, result[0].id)
        assertEquals("kotlin", result[0].name)
        assertEquals(4, result[0].pageCount)
    }

    private data class TestTagWithPageCountView(
        private val id: UUID,
        private val name: String,
        private val pageCount: Long
    ) : TagWithPageCountView {
        override fun getId(): UUID = id
        override fun getName(): String = name
        override fun getPageCount(): Long = pageCount
    }
}
