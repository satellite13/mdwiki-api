package com.mdwiki.service.usecase

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.error.NotFoundException
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UpdateUserRoleUseCase(
    private val userRepository: UserRepository
) {
    fun execute(userId: UUID, request: UpdateUserRoleRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }
        user.role = request.role
        val saved = userRepository.save(user)
        return saved.toResponse()
    }
}
