package com.mdwiki.service.usecase

import com.mdwiki.dto.TagResponse
import com.mdwiki.repository.TagRepository

class ListTagsUseCase(
    private val tagRepository: TagRepository
) {
    fun execute(): List<TagResponse> {
        return tagRepository.findAllWithPageCount().map {
            TagResponse(id = it.getId(), name = it.getName(), pageCount = it.getPageCount())
        }
    }
}
