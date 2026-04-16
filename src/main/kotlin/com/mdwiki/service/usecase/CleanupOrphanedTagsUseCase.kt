package com.mdwiki.service.usecase

import com.mdwiki.repository.TagRepository

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
