package com.mdwiki.controller

import com.mdwiki.model.Tag
import com.mdwiki.service.TagService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tags")
class TagController(private val tagService: TagService) {

    @GetMapping
    fun list(): List<Tag> = tagService.findAll()
}
