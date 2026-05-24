package com.mdwiki.dto

data class GraphNode(
    val slug: String,
    val title: String,
    val tags: List<String>,
    val isCurrent: Boolean,
    val exists: Boolean = true
)

data class GraphEdge(
    val source: String,
    val target: String
)

data class GraphResponse(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)
