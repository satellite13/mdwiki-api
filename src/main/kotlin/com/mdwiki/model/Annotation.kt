package com.mdwiki.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "annotations")
class Annotation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "page_id", nullable = false)
    var pageId: UUID,

    @Column(name = "anchor_context", columnDefinition = "text")
    var anchorContext: String,

    @Column(name = "highlighted_text", nullable = false, columnDefinition = "text")
    var highlightedText: String,

    @Column(columnDefinition = "text")
    var comment: String? = null,

    @Column(name = "range_start")
    var rangeStart: Int? = null,

    @Column(name = "range_end")
    var rangeEnd: Int? = null,

    @Column(length = 20)
    var color: String? = null,

    @Column(name = "created_by", nullable = false, length = 100)
    var createdBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
