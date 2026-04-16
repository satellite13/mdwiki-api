package com.mdwiki.service

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.service.usecase.DeleteUserUseCase
import com.mdwiki.service.usecase.ListUsersUseCase
import com.mdwiki.service.usecase.UpdateUserRoleUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val listUsersUseCase: ListUsersUseCase,
    private val updateUserRoleUseCase: UpdateUserRoleUseCase,
    private val deleteUserUseCase: DeleteUserUseCase
) {
    @Transactional(readOnly = true)
    fun findAll(): List<UserResponse> = listUsersUseCase.execute()

    @Transactional
    fun updateRole(userId: UUID, request: UpdateUserRoleRequest): UserResponse =
        updateUserRoleUseCase.execute(userId, request)

    @Transactional
    fun delete(userId: UUID, actorUsername: String) = deleteUserUseCase.execute(userId, actorUsername)
}
