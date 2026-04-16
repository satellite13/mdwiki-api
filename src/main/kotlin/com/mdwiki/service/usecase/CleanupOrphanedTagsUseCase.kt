package com.mdwiki.service.usecase

import com.mdwiki.repository.TagRepository
import org.springframework.stereotype.Component

@Component
class CleanupOrphanedTagsUseCase(
    private val tagRepository: TagRepository
) {
    fun execute() {
        val orphaned = tagRepository.findOrphanedTags()
        if (orphaned.isNotEmpty()) {
            tagRepository.deleteAll(orphaned)
        }
    }
}
