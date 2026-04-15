package com.mdwiki.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "links")
class Link(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_page_id", nullable = false)
    var sourcePage: Page,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_page_id")
    var targetPage: Page? = null,

    @Column(name = "target_slug", nullable = false, length = 500)
    var targetSlug: String
)
