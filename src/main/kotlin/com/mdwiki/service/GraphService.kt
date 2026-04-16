package com.mdwiki.service

import com.mdwiki.dto.GraphEdge
import com.mdwiki.dto.GraphNode
import com.mdwiki.dto.GraphResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service

@Service
class GraphService(
    private val pageRepository: PageRepository,
    private val linkRepository: LinkRepository
) {

    fun getGraph(slug: String, depth: Int): GraphResponse {
        val page = pageRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Page not found: $slug")

        val visitedSlugs = mutableSetOf(slug)
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // Add current page as node
        nodes.add(GraphNode(slug = page.slug, title = page.title, tags = page.tags.map { it.name }, isCurrent = true))

        // BFS expansion
        var frontier = setOf(slug)
        for (level in 1..depth.coerceIn(1, 3)) {
            val nextFrontier = mutableSetOf<String>()
            for (currentSlug in frontier) {
                val currentPage = pageRepository.findBySlugAndDeletedAtIsNull(currentSlug) ?: continue

                // Outgoing links
                val outgoing = linkRepository.findBySourcePage(currentPage)
                for (link in outgoing) {
                    val targetSlug = link.targetSlug
                    edges.add(GraphEdge(source = currentSlug, target = targetSlug))
                    if (targetSlug !in visitedSlugs) {
                        visitedSlugs.add(targetSlug)
                        nextFrontier.add(targetSlug)
                        val targetPage = link.targetPage ?: pageRepository.findBySlugAndDeletedAtIsNull(targetSlug)
                        if (targetPage != null) {
                            nodes.add(GraphNode(slug = targetPage.slug, title = targetPage.title, tags = targetPage.tags.map { it.name }, isCurrent = false))
                        } else {
                            // Dangling link — page doesn't exist yet
                            nodes.add(GraphNode(slug = targetSlug, title = targetSlug, tags = emptyList(), isCurrent = false))
                        }
                    }
                }

                // Backlinks (incoming)
                val incoming = linkRepository.findByTargetSlug(currentSlug)
                for (link in incoming) {
                    val sourceSlug = link.sourcePage.slug
                    edges.add(GraphEdge(source = sourceSlug, target = currentSlug))
                    if (sourceSlug !in visitedSlugs) {
                        visitedSlugs.add(sourceSlug)
                        nextFrontier.add(sourceSlug)
                        nodes.add(GraphNode(slug = link.sourcePage.slug, title = link.sourcePage.title, tags = link.sourcePage.tags.map { it.name }, isCurrent = false))
                    }
                }
            }
            frontier = nextFrontier
        }

        // Deduplicate edges
        val uniqueEdges = edges.distinctBy { "${it.source}->${it.target}" }

        return GraphResponse(nodes = nodes, edges = uniqueEdges)
    }

    /**
     * Returns slugs of neighbor pages within given depth (for RAG graph expansion).
     */
    fun getNeighborSlugs(slug: String, depth: Int): Set<String> {
        val visited = mutableSetOf(slug)
        var frontier = setOf(slug)

        for (level in 1..depth.coerceIn(1, 2)) {
            val nextFrontier = mutableSetOf<String>()
            for (currentSlug in frontier) {
                val currentPage = pageRepository.findBySlugAndDeletedAtIsNull(currentSlug) ?: continue

                // Outgoing
                linkRepository.findBySourcePage(currentPage).forEach { link ->
                    if (link.targetSlug !in visited) {
                        visited.add(link.targetSlug)
                        nextFrontier.add(link.targetSlug)
                    }
                }

                // Incoming
                linkRepository.findByTargetSlug(currentSlug).forEach { link ->
                    val srcSlug = link.sourcePage.slug
                    if (srcSlug !in visited) {
                        visited.add(srcSlug)
                        nextFrontier.add(srcSlug)
                    }
                }
            }
            frontier = nextFrontier
        }

        visited.remove(slug) // exclude the original page
        return visited
    }
}
