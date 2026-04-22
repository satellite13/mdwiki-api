package com.mdwiki.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "embedding_runtime_settings")
class EmbeddingRuntimeSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "singleton_key", nullable = false, unique = true, length = 64)
    val singletonKey: String = SINGLETON_KEY,

    @Column(nullable = false, length = 32)
    var provider: String,

    @Column(nullable = false, length = 255)
    var model: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    companion object {
        const val SINGLETON_KEY: String = "default"
    }
}
