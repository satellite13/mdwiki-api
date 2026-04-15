package com.mdwiki.service

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {

    fun findAll(): List<UserResponse> {
        return userRepository.findAll().map {
            UserResponse(id = it.id!!, username = it.username, email = it.email, role = it.role)
        }
    }

    fun updateRole(userId: UUID, request: UpdateUserRoleRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        user.role = request.role
        val saved = userRepository.save(user)
        return UserResponse(id = saved.id!!, username = saved.username, email = saved.email, role = saved.role)
    }
}
