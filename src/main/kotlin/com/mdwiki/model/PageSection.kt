package com.mdwiki.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "page_sections",
    uniqueConstraints = [UniqueConstraint(columnNames = ["page_id", "stable_key"])]
)
class PageSection(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    var page: Page,

    @Column(name = "stable_key", nullable = false)
    var stableKey: String,

    @Column
    var heading: String? = null,

    @Column(name = "heading_level", nullable = false)
    var headingLevel: Int,

    @Column(name = "heading_path", nullable = false)
    var headingPath: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

    @Column(name = "start_offset", nullable = false)
    var startOffset: Int,

    @Column(name = "end_offset", nullable = false)
    var endOffset: Int,

    @Column(name = "content_hash", nullable = false)
    var contentHash: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
