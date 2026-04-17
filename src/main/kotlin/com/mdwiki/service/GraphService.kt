package com.mdwiki.service

import com.mdwiki.dto.GraphEdge
import com.mdwiki.dto.GraphNode
import com.mdwiki.dto.GraphResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GraphService(
    private val pageRepository: PageRepository,
    private val linkRepository: LinkRepository
) {

    @Transactional(readOnly = true)
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
                    val targetPage = link.targetPage ?: pageRepository.findBySlugAndDeletedAtIsNull(targetSlug)
                    // В БД target_slug может не совпадать со slug страницы (старые ссылки / rename);
                    // в API графа рёбра и узлы должны совпадать по каноническому slug страницы.
                    val canonicalTarget = targetPage?.slug ?: targetSlug
                    edges.add(GraphEdge(source = currentSlug, target = canonicalTarget))
                    if (canonicalTarget !in visitedSlugs) {
                        visitedSlugs.add(canonicalTarget)
                        nextFrontier.add(canonicalTarget)
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
     * Граф всей вики: все неудалённые страницы и рёбра из links.
     * [highlight] — подсветить узел с этим slug (как «текущая» страница в UI).
     */
    @Transactional(readOnly = true)
    fun getFullWikiGraph(highlight: String?): GraphResponse {
        val pages = pageRepository.findAllByDeletedAtIsNull()
        val knownSlugs = pages.map { it.slug }.toMutableSet()
        val nodes = pages.map { p ->
            GraphNode(
                slug = p.slug,
                title = p.title,
                tags = p.tags.map { it.name },
                isCurrent = highlight != null && p.slug == highlight
            )
        }.toMutableList()

        val edges = mutableListOf<GraphEdge>()
        for (link in linkRepository.findAll()) {
            val src = link.sourcePage
            if (src.deletedAt != null) continue
            val srcSlug = src.slug
            if (srcSlug !in knownSlugs) continue

            val targetPage = link.targetPage ?: pageRepository.findBySlugAndDeletedAtIsNull(link.targetSlug)
            if (targetPage != null && targetPage.deletedAt != null) continue
            val canonicalTarget = targetPage?.slug ?: link.targetSlug

            edges.add(GraphEdge(source = srcSlug, target = canonicalTarget))

            if (canonicalTarget !in knownSlugs) {
                if (targetPage != null) {
                    nodes.add(
                        GraphNode(
                            slug = targetPage.slug,
                            title = targetPage.title,
                            tags = targetPage.tags.map { it.name },
                            isCurrent = highlight != null && targetPage.slug == highlight
                        )
                    )
                    knownSlugs.add(targetPage.slug)
                } else {
                    nodes.add(GraphNode(slug = canonicalTarget, title = canonicalTarget, tags = emptyList(), isCurrent = false))
                    knownSlugs.add(canonicalTarget)
                }
            }
        }

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
