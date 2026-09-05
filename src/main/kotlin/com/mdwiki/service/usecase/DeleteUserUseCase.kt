package com.mdwiki.service.usecase

import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import com.mdwiki.repository.FolderRepository
import com.mdwiki.repository.PageRepository
import com.mdwiki.repository.AttachmentRepository
import com.mdwiki.service.FolderService
import com.mdwiki.service.AttachmentService
import com.mdwiki.dto.FolderDeletePageAction
import com.mdwiki.config.WikiProperties
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Component
class DeleteUserUseCase(
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val pageRepository: PageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val folderService: FolderService,
    private val attachmentService: AttachmentService,
    private val wikiProperties: WikiProperties
) {
    @Transactional
    fun execute(userId: UUID, actorUsername: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        if (user.username == actorUsername) {
            throw ForbiddenException("Cannot delete your own account")
        }
        if (user.role == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1L) {
            throw ForbiddenException("Cannot delete the last admin")
        }

        val ownedFolders = folderRepository.findAllByOwnerId(userId)
        val ownedPageIds = ownedFolders.flatMap { folder ->
            pageRepository.findByFolderId(folder.id!!).mapNotNull { it.id }
        }.distinct()
        attachmentRepository.findByPageIdIn(ownedPageIds)
            .distinctBy { it.id }
            .forEach { attachmentService.deletePreAuthorized(it.id!!) }
        ownedFolders.filter { it.parent == null }
            .forEach { folderService.delete(it.id!!, actorUsername, FolderDeletePageAction.DELETE) }
        userRepository.delete(user)
        userRepository.flush()
        Files.deleteIfExists(
            Path.of(wikiProperties.contentDir).toAbsolutePath().normalize()
                .resolve(".pkm").resolve(userId.toString())
        )
    }
}
