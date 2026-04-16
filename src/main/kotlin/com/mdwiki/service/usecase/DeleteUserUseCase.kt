package com.mdwiki.service.usecase

import com.mdwiki.error.ForbiddenException
import com.mdwiki.error.NotFoundException
import com.mdwiki.model.UserRole
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DeleteUserUseCase(
    private val userRepository: UserRepository
) {
    fun execute(userId: UUID, actorUsername: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        if (user.username == actorUsername) {
            throw ForbiddenException("Cannot delete your own account")
        }
        if (user.role == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1L) {
            throw ForbiddenException("Cannot delete the last admin")
        }

        userRepository.delete(user)
    }
}
