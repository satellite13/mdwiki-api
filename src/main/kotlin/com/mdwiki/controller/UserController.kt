package com.mdwiki.controller

import com.mdwiki.dto.UpdateUserRoleRequest
import com.mdwiki.dto.UserResponse
import com.mdwiki.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
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

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable userId: UUID, auth: Authentication) {
        userService.delete(userId, auth.name)
    }
}
