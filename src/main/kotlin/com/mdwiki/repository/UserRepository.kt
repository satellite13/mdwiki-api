package com.mdwiki.repository

import com.mdwiki.model.User
import com.mdwiki.model.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun countByRole(role: UserRole): Long
}
