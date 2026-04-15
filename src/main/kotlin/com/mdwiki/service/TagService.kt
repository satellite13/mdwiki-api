package com.mdwiki.service

import com.mdwiki.model.Tag
import com.mdwiki.repository.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TagService(private val tagRepository: TagRepository) {

    fun findAll(): List<Tag> = tagRepository.findAll()

    @Transactional
    fun getOrCreateTags(names: Set<String>): Set<Tag> {
        if (names.isEmpty()) return emptySet()
        val existing = tagRepository.findByNameIn(names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }.map { tagRepository.save(Tag(name = it)) }
        return (existing + newTags).toSet()
    }

    @Transactional
    fun cleanupOrphanedTags() {
        val orphaned = tagRepository.findOrphanedTags()
        if (orphaned.isNotEmpty()) {
            tagRepository.deleteAll(orphaned)
        }
    }
}
