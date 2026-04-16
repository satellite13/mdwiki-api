package com.mdwiki.service.usecase

import com.mdwiki.dto.UserResponse
import com.mdwiki.mapper.toResponse
import com.mdwiki.repository.UserRepository

class ListUsersUseCase(
    private val userRepository: UserRepository
) {
    fun execute(): List<UserResponse> {
        return userRepository.findAll().map { it.toResponse() }
    }
}
