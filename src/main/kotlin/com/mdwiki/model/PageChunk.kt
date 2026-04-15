package com.mdwiki.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "page_chunks")
class PageChunk(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    var page: Page,

    @Column(name = "chunk_index", nullable = false)
    var chunkIndex: Int,

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    var chunkText: String,

    @Column(name = "section_heading", length = 500)
    var sectionHeading: String? = null
)
