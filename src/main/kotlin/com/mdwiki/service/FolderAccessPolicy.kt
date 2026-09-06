package com.mdwiki.service

import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.Folder
import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class FolderAccessPolicy(private val users: UserRepository) {
    fun actor(username: String): User = users.findByUsername(username)
        ?: throw NotFoundException("User not found: $username")

    fun requireAccess(folder: Folder, username: String): User {
        val actor = actor(username)
        val ownerId = folder.owner?.id
        if (ownerId != null && actor.role != UserRole.ADMIN && ownerId != actor.id) {
            throw ForbiddenException("Folder belongs to another user")
        }
        return actor
    }

    fun requireCreate(parent: Folder?, username: String): User {
        val actor = actor(username)
        if (parent != null && parent.owner != null && actor.role != UserRole.ADMIN && parent.owner?.id != actor.id) {
            throw ForbiddenException("Cannot create a folder in another user's owned root")
        }
        return actor
    }

    fun requireMove(folder: Folder, targetParent: Folder?, username: String) {
        requireAccess(folder, username)
        if (targetParent != null) requireAccess(targetParent, username)
        if (targetParent == null && folder.parent == null) return
        if (folder.owner?.id != targetParent?.owner?.id) {
            throw ForbiddenException("Folder moves must preserve ownership scope")
        }
    }

    fun requireDeleteSubtree(
        folders: Collection<Folder>,
        username: String,
        movePagesToRoot: Boolean = false
    ) {
        val actor = actor(username)
        if (movePagesToRoot && folders.any { it.owner != null }) {
            throw ForbiddenException("Pages in owned folders cannot be moved to the shared root")
        }
        if (actor.role == UserRole.ADMIN) return
        if (folders.any { it.owner?.id != null && it.owner?.id != actor.id }) {
            throw ForbiddenException("Folder subtree contains another user's owned folder")
        }
    }
}
