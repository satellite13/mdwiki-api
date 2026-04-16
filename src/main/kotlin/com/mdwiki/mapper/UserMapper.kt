package com.mdwiki.mapper

import com.mdwiki.dto.UserResponse
import com.mdwiki.model.User

fun User.toResponse(): UserResponse = UserResponse(
    id = id!!,
    username = username,
    email = email,
    role = role
)
