package com.mdwiki.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "tags")
class Tag(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true, length = 200)
    var name: String
)
