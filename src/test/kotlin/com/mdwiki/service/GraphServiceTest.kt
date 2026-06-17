package com.mdwiki.service

import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Link
import com.mdwiki.model.Page
import com.mdwiki.model.Tag
import com.mdwiki.repository.LinkRepository
import com.mdwiki.repository.PageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceTest {

    @Mock
    private lateinit var pageRepository: PageRepository

    @Mock
    private lateinit var linkRepository: LinkRepository

    private lateinit var graphService: GraphService

    private val tagKotlin = Tag(name = "kotlin")

    @BeforeEach
    fun setUp() {
        graphService = GraphService(pageRepository, linkRepository, WikilinkService())
    }

    @Test
    fun `getGraph throws when root page missing`() {
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("missing")).thenReturn(null)

        val ex = assertThrows<NotFoundException> {
            graphService.getGraph("missing", depth = 1)
        }
        assertTrue(ex.message!!.contains("missing"))
    }

    @Test
    fun `getGraph includes dangling target as node`() {
        val root = page("root", "Root", tags = listOf(tagKotlin), content = "See [[ghost]]")
        val danglingLink = Link(sourcePage = root, targetPage = null, targetSlug = "ghost")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("root")).thenReturn(root)
        whenever(linkRepository.findBySourcePage(root)).thenReturn(listOf(danglingLink))
        whenever(linkRepository.findByTargetSlug("root")).thenReturn(emptyList())
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("ghost")).thenReturn(null)

        val graph = graphService.getGraph("root", depth = 1)

        assertEquals(
            setOf("root", "ghost"),
            graph.nodes.map { it.slug }.toSet()
        )
        assertEquals(
            listOf(com.mdwiki.dto.GraphEdge("root", "ghost")),
            graph.edges
        )
        val ghost = graph.nodes.find { it.slug == "ghost" }!!
        assertEquals("ghost", ghost.title)
        assertTrue(ghost.tags.isEmpty())
    }

    @Test
    fun `getGraph ignores stale links when wikilink only appears in code`() {
        val root = page("root", "Root", content = "Docs: `[[ghost]]` and `[[slug|Title]]`")
        val staleLink = Link(sourcePage = root, targetPage = null, targetSlug = "ghost")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("root")).thenReturn(root)
        whenever(linkRepository.findBySourcePage(root)).thenReturn(listOf(staleLink))
        whenever(linkRepository.findByTargetSlug("root")).thenReturn(emptyList())

        val graph = graphService.getGraph("root", depth = 1)

        assertEquals(setOf("root"), graph.nodes.map { it.slug }.toSet())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `getGraph edge target uses resolved page slug when link targetSlug differs`() {
        val root = page("mcp", "MCP протокол", content = "See [[настройка-mcp-для-mdwiki]]")
        val guide = page("mcp-setup-guide", "Настройка MCP для mdwiki")
        val link = Link(
            sourcePage = root,
            targetPage = guide,
            targetSlug = "настройка-mcp-для-mdwiki"
        )

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("mcp")).thenReturn(root)
        whenever(linkRepository.findBySourcePage(root)).thenReturn(listOf(link))
        whenever(linkRepository.findByTargetSlug("mcp")).thenReturn(emptyList())

        val graph = graphService.getGraph("mcp", depth = 1)

        assertEquals(
            listOf(com.mdwiki.dto.GraphEdge("mcp", "mcp-setup-guide")),
            graph.edges
        )
        assertTrue(graph.nodes.any { it.slug == "mcp-setup-guide" })
    }

    @Test
    fun `getGraph deduplicates parallel edges`() {
        val root = page("root", "Root", content = "[[b]]")
        val target = page("b", "B")
        val link1 = Link(sourcePage = root, targetPage = target, targetSlug = "b")
        val link2 = Link(sourcePage = root, targetPage = target, targetSlug = "b")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("root")).thenReturn(root)
        whenever(linkRepository.findBySourcePage(root)).thenReturn(listOf(link1, link2))
        whenever(linkRepository.findByTargetSlug("root")).thenReturn(emptyList())
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("b")).thenReturn(target)

        val graph = graphService.getGraph("root", depth = 1)

        assertEquals(1, graph.edges.count { it.source == "root" && it.target == "b" })
    }

    @Test
    fun `getGraph expands incoming backlinks`() {
        val root = page("root", "Root")
        val other = page("other", "Other", content = "Back to [[root]]")
        val back = Link(sourcePage = other, targetPage = root, targetSlug = "root")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("root")).thenReturn(root)
        whenever(linkRepository.findBySourcePage(root)).thenReturn(emptyList())
        whenever(linkRepository.findByTargetSlug("root")).thenReturn(listOf(back))

        val graph = graphService.getGraph("root", depth = 1)

        assertTrue(graph.nodes.any { it.slug == "other" })
        assertTrue(graph.edges.any { it.source == "other" && it.target == "root" })
    }

    @Test
    fun `getGraph clamps depth to at most three levels`() {
        val a = page("a", "A", content = "[[b]]")
        val b = page("b", "B", content = "[[c]]")
        val c = page("c", "C", content = "[[d]]")
        val d = page("d", "D")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("a")).thenReturn(a)
        whenever(linkRepository.findByTargetSlug(any())).thenReturn(emptyList())

        whenever(linkRepository.findBySourcePage(a)).thenReturn(
            listOf(Link(sourcePage = a, targetPage = b, targetSlug = "b"))
        )
        whenever(linkRepository.findBySourcePage(b)).thenReturn(
            listOf(Link(sourcePage = b, targetPage = c, targetSlug = "c"))
        )
        whenever(linkRepository.findBySourcePage(c)).thenReturn(
            listOf(Link(sourcePage = c, targetPage = d, targetSlug = "d"))
        )
        whenever(linkRepository.findBySourcePage(d)).thenReturn(emptyList())

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("b")).thenReturn(b)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("c")).thenReturn(c)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("d")).thenReturn(d)

        val graph = graphService.getGraph("a", depth = 100)

        assertTrue(graph.nodes.any { it.slug == "d" })
    }

    @Test
    fun `getFullWikiGraph includes all pages and links`() {
        val a = page("a", "A", content = "[[b]]")
        val b = page("b", "B")
        val link = Link(sourcePage = a, targetPage = b, targetSlug = "b")

        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(a, b))
        whenever(linkRepository.findAll()).thenReturn(listOf(link))

        val graph = graphService.getFullWikiGraph(highlight = null)

        assertEquals(setOf("a", "b"), graph.nodes.map { it.slug }.toSet())
        assertEquals(listOf(com.mdwiki.dto.GraphEdge("a", "b")), graph.edges)
        assertTrue(graph.nodes.none { it.isCurrent })
    }

    @Test
    fun `getFullWikiGraph marks highlight node as current`() {
        val a = page("a", "A")
        val b = page("b", "B")

        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(a, b))
        whenever(linkRepository.findAll()).thenReturn(emptyList())

        val graph = graphService.getFullWikiGraph(highlight = "b")

        assertTrue(graph.nodes.find { it.slug == "b" }!!.isCurrent)
        assertTrue(graph.nodes.find { it.slug == "a" }!!.isCurrent.not())
    }

    @Test
    fun `getFullWikiGraph skips deleted source links`() {
        val a = page("a", "A", content = "[[b]]")
        a.deletedAt = java.time.Instant.now()
        val b = page("b", "B")
        val link = Link(sourcePage = a, targetPage = b, targetSlug = "b")

        whenever(pageRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(b))
        whenever(linkRepository.findAll()).thenReturn(listOf(link))

        val graph = graphService.getFullWikiGraph(null)

        assertEquals(setOf("b"), graph.nodes.map { it.slug }.toSet())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `getNeighborSlugs returns neighbors excluding root`() {
        val a = page("a", "A", content = "[[b]]")
        val b = page("b", "B")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull("a")).thenReturn(a)
        whenever(pageRepository.findBySlugAndDeletedAtIsNull("b")).thenReturn(b)
        whenever(linkRepository.findBySourcePage(a)).thenReturn(
            listOf(Link(sourcePage = a, targetPage = b, targetSlug = "b"))
        )
        whenever(linkRepository.findByTargetSlug("a")).thenReturn(emptyList())
        whenever(linkRepository.findBySourcePage(b)).thenReturn(emptyList())
        whenever(linkRepository.findByTargetSlug("b")).thenReturn(emptyList())

        val neighbors = graphService.getNeighborSlugs("a", depth = 2)

        assertEquals(setOf("b"), neighbors)
    }

    @Test
    fun `getNeighborSlugs clamps depth to two`() {
        val a = page("a", "A", content = "[[b]]")
        val b = page("b", "B", content = "[[c]]")
        val c = page("c", "C")

        whenever(pageRepository.findBySlugAndDeletedAtIsNull(any())).thenAnswer { inv ->
            when (inv.getArgument<String>(0)) {
                "a" -> a
                "b" -> b
                "c" -> c
                else -> null
            }
        }
        whenever(linkRepository.findBySourcePage(a)).thenReturn(
            listOf(Link(sourcePage = a, targetPage = b, targetSlug = "b"))
        )
        whenever(linkRepository.findBySourcePage(b)).thenReturn(
            listOf(Link(sourcePage = b, targetPage = c, targetSlug = "c"))
        )
        whenever(linkRepository.findBySourcePage(c)).thenReturn(emptyList())
        whenever(linkRepository.findByTargetSlug(any())).thenReturn(emptyList())

        val neighbors = graphService.getNeighborSlugs("a", depth = 10)

        assertEquals(setOf("b", "c"), neighbors)
    }

    private fun page(
        slug: String,
        title: String,
        tags: List<Tag> = emptyList(),
        content: String = "",
    ): Page {
        val p = Page(slug = slug, title = title, contentMd = content)
        tags.forEach { p.tags.add(it) }
        return p
    }
}
