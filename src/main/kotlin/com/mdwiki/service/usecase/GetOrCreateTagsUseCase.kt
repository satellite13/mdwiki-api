package com.mdwiki.service.usecase

import com.mdwiki.model.Tag
import com.mdwiki.repository.TagRepository
import org.springframework.stereotype.Component

@Component
class GetOrCreateTagsUseCase(
    private val tagRepository: TagRepository
) {
    fun execute(names: Set<String>): Set<Tag> {
        if (names.isEmpty()) return emptySet()
        val existing = tagRepository.findByNameIn(names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }.map { tagRepository.save(Tag(name = it)) }
        return (existing + newTags).toSet()
    }
}
