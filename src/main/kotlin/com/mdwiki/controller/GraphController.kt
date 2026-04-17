package com.mdwiki.controller

import com.mdwiki.dto.GraphResponse
import com.mdwiki.service.GraphService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/graph")
class GraphController(
    private val graphService: GraphService
) {

    /** Все страницы (не удалённые) и все связи из таблицы links. */
    @GetMapping("/wiki")
    fun wikiGraph(@RequestParam(required = false) highlight: String?): GraphResponse =
        graphService.getFullWikiGraph(highlight)
}
