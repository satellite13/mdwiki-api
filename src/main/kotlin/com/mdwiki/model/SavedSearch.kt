package com.mdwiki.model

import com.mdwiki.util.PersistentInstant
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class SavedSearchMode { HYBRID, TEXT, SEMANTIC }
enum class SavedSearchSort { RELEVANCE, UPDATED }

@Entity
@Table(name = "saved_searches")
class SavedSearch(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Column(nullable = false, length = 120)
    var name: String,
    @Column(name = "query_text", nullable = false, length = 1000)
    var queryText: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    var mode: SavedSearchMode,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var tags: List<String> = emptyList(),
    @Column(name = "min_score")
    var minScore: Double? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    var sort: SavedSearchSort = SavedSearchSort.RELEVANCE,
    @Column(nullable = false)
    var version: Long = 1,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = PersistentInstant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = PersistentInstant.now()
)
