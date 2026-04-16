package com.mdwiki.service

import com.mdwiki.dto.CreateFolderRequest
import com.mdwiki.dto.MoveFolderRequest
import com.mdwiki.dto.UpdateFolderRequest
import com.mdwiki.model.Folder
import com.mdwiki.model.Page
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.*
import java.io.File
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FolderServiceTest {

    @Mock private lateinit var folderRepository: FolderRepository
    @Mock private lateinit var pageRepository: PageRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var wikiFileService: WikiFileService
    @Mock private lateinit var treeEventsService: TreeEventsService

    private lateinit var folderService: FolderService

    @BeforeEach
    fun setUp() {
        val stubDir = File(System.getProperty("java.io.tmpdir"))
        whenever(wikiFileService.ensureFolderDirectory(any())).thenReturn(stubDir)
        whenever(wikiFileService.resolveFolderDirectory(any())).thenReturn(stubDir)
        doNothing().whenever(wikiFileService).moveFolderDirectory(any(), any())
        doNothing().whenever(wikiFileService).deleteFolderDirectory(any())
        doNothing().whenever(wikiFileService).relocatePageFile(any(), any())
        folderService = FolderService(
            folderRepository,
            pageRepository,
            userRepository,
            wikiFileService,
            treeEventsService
        )
    }

    @Test
    fun `getTree builds correct hierarchy`() {
        val rootFolder = Folder(id = UUID.randomUUID(), name = "docs")
        val childFolder = Folder(id = UUID.randomUUID(), name = "guides", parent = rootFolder)

        val rootPage = Page(id = UUID.randomUUID(), slug = "home", title = "Home")
        val childPage = Page(id = UUID.randomUUID(), slug = "guide-1", title = "Guide 1", folder = childFolder)

        whenever(folderRepository.findAll()).thenReturn(listOf(rootFolder, childFolder))
        whenever(pageRepository.findAll()).thenReturn(listOf(rootPage, childPage))

        val tree = folderService.getTree()

        assertEquals(2, tree.size) // root folder + root page
        val folderNode = tree.first { it.type == "folder" }
        assertEquals("docs", folderNode.name)
        assertEquals("folder-${rootFolder.id}", folderNode.id)

        // Child folder inside docs
        assertEquals(1, folderNode.children.size)
        val guidesNode = folderNode.children[0]
        assertEquals("guides", guidesNode.name)
        assertEquals("folder", guidesNode.type)

        // Page inside guides
        assertEquals(1, guidesNode.children.size)
        assertEquals("Guide 1", guidesNode.children[0].name)
        assertEquals("page", guidesNode.children[0].type)
        assertEquals("guide-1", guidesNode.children[0].slug)

        // Root page
        val pageNode = tree.first { it.type == "page" }
        assertEquals("Home", pageNode.name)
        assertEquals("home", pageNode.slug)
    }

    @Test
    fun `create folder`() {
        val user = User(id = UUID.randomUUID(), username = "testuser", email = "t@t.com", passwordHash = "h")
        whenever(userRepository.findByUsername("testuser")).thenReturn(user)
        whenever(folderRepository.existsByParentIdAndName(null, "new-folder")).thenReturn(false)
        whenever(folderRepository.save(any<Folder>())).thenAnswer {
            val f = it.arguments[0] as Folder
            Folder(id = UUID.randomUUID(), name = f.name, parent = f.parent, createdBy = f.createdBy)
        }

        val result = folderService.create(CreateFolderRequest(name = "new-folder"), "testuser")

        assertEquals("new-folder", result.name)
        assertNull(result.parentId)
        verify(folderRepository).save(argThat<Folder> { name == "new-folder" })
    }

    @Test
    fun `create folder throws on duplicate name`() {
        whenever(folderRepository.existsByParentIdAndName(null, "existing")).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            folderService.create(CreateFolderRequest(name = "existing"), "testuser")
        }
    }

    @Test
    fun `rename folder`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "old-name")
        whenever(folderRepository.findById(folderId)).thenReturn(Optional.of(folder))
        whenever(folderRepository.existsByParentIdAndName(null, "new-name")).thenReturn(false)
        whenever(folderRepository.save(any<Folder>())).thenAnswer { it.arguments[0] }

        val result = folderService.rename(folderId, UpdateFolderRequest(name = "new-name"))

        assertEquals("new-name", result.name)
    }

    @Test
    fun `move folder`() {
        val folderId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "movable")
        val target = Folder(id = targetId, name = "target")

        whenever(folderRepository.findById(folderId)).thenReturn(Optional.of(folder))
        whenever(folderRepository.findById(targetId)).thenReturn(Optional.of(target))
        whenever(folderRepository.save(any<Folder>())).thenAnswer { it.arguments[0] }

        val result = folderService.move(folderId, MoveFolderRequest(parentId = targetId))

        assertEquals(targetId, result.parentId)
    }

    @Test
    fun `move folder rejects circular reference`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val parent = Folder(id = parentId, name = "parent")
        val child = Folder(id = childId, name = "child", parent = parent)

        whenever(folderRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(folderRepository.findById(childId)).thenReturn(Optional.of(child))

        assertThrows<IllegalArgumentException> {
            folderService.move(parentId, MoveFolderRequest(parentId = childId))
        }
    }

    @Test
    fun `move folder rejects self-reference`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "self")

        whenever(folderRepository.findById(folderId)).thenReturn(Optional.of(folder))

        assertThrows<IllegalArgumentException> {
            folderService.move(folderId, MoveFolderRequest(parentId = folderId))
        }
    }

    @Test
    fun `delete folder cascades to children`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val grandchildId = UUID.randomUUID()
        val parent = Folder(id = parentId, name = "parent")
        val child = Folder(id = childId, name = "child", parent = parent)
        val grandchild = Folder(id = grandchildId, name = "grandchild", parent = child)

        whenever(folderRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(folderRepository.findAll()).thenReturn(listOf(parent, child, grandchild))
        whenever(pageRepository.findAll()).thenReturn(emptyList())

        folderService.delete(parentId)

        verify(folderRepository).delete(parent)
    }

    @Test
    fun `delete folder nullifies page folder references`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "doomed")
        val page = Page(id = UUID.randomUUID(), slug = "orphan", title = "Orphan", folder = folder)

        whenever(folderRepository.findById(folderId)).thenReturn(Optional.of(folder))
        whenever(folderRepository.findAll()).thenReturn(listOf(folder))
        whenever(pageRepository.findAll()).thenReturn(listOf(page))

        folderService.delete(folderId)

        assertNull(page.folder)
        verify(pageRepository).saveAll(listOf(page))
        verify(folderRepository).delete(folder)
    }
}
