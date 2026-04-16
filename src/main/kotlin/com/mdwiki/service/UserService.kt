package com.mdwiki.service

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.repository.UserRepository
import com.mdwiki.service.usecase.ListUsersUseCase
import com.mdwiki.service.usecase.UpdateUserRoleUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {
    private val listUsersUseCase = ListUsersUseCase(userRepository)
    private val updateUserRoleUseCase = UpdateUserRoleUseCase(userRepository)

    @Transactional(readOnly = true)
    fun findAll(): List<UserResponse> = listUsersUseCase.execute()

    @Transactional
    fun updateRole(userId: UUID, request: UpdateUserRoleRequest): UserResponse =
        updateUserRoleUseCase.execute(userId, request)
}
