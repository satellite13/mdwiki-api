package com.mdwiki.dto

import com.mdwiki.model.UserRole
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val role: UserRole
)

data class UpdateUserRoleRequest(
    val role: UserRole
)
