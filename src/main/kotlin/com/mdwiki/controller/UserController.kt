package com.mdwiki.controller

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.service.UserService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun list(): List<UserResponse> = userService.findAll()

    @PutMapping("/{userId}/role")
    fun updateRole(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateUserRoleRequest
    ): UserResponse {
        return userService.updateRole(userId, request)
    }
}
