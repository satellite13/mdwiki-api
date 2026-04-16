package com.mdwiki.controller

import com.mdwiki.dto.TagResponse
import com.mdwiki.service.TagService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tags")
class TagController(private val tagService: TagService) {

    @GetMapping
    fun list(): List<TagResponse> = tagService.findAll()
}
