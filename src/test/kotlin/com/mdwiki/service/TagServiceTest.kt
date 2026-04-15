package com.mdwiki.service

import com.mdwiki.model.Tag
import com.mdwiki.repository.TagRepository
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
}
