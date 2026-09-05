package com.mdwiki.model

import com.mdwiki.util.PersistentInstant
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "section_anchors")
class SectionAnchor(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    val page: Page,
    @Column(name = "stable_id", nullable = false, unique = true, length = 128)
    val stableId: String,
    @Column(name = "last_section_key", nullable = false, length = 1000)
    var lastSectionKey: String,
    @Column(name = "last_heading_path", nullable = false, length = 2000)
    var lastHeadingPath: String,
    @Column(name = "retired_at")
    var retiredAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = PersistentInstant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = PersistentInstant.now()
)
