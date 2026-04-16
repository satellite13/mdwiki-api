package com.mdwiki.service

import com.mdwiki.dto.TagResponse
import com.mdwiki.model.Tag
import com.mdwiki.service.usecase.CleanupOrphanedTagsUseCase
import com.mdwiki.service.usecase.GetOrCreateTagsUseCase
import com.mdwiki.service.usecase.ListTagsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TagService(
    private val listTagsUseCase: ListTagsUseCase,
    private val getOrCreateTagsUseCase: GetOrCreateTagsUseCase,
    private val cleanupOrphanedTagsUseCase: CleanupOrphanedTagsUseCase
) {
    @Transactional(readOnly = true)
    fun findAll(): List<TagResponse> = listTagsUseCase.execute()

    @Transactional
    fun getOrCreateTags(names: Set<String>): Set<Tag> = getOrCreateTagsUseCase.execute(names)

    @Transactional
    fun cleanupOrphanedTags() = cleanupOrphanedTagsUseCase.execute()
}
