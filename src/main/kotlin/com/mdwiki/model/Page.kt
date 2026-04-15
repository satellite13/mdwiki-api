package com.mdwiki.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "pages")
class Page(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true, length = 500)
    var slug: String,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(name = "content_md", columnDefinition = "text")
    var contentMd: String? = null,

    @Column(name = "content_html", columnDefinition = "text")
    var contentHtml: String? = null,

    @Column(name = "file_path", length = 1000)
    var filePath: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    var updatedBy: User? = null,

    @ManyToMany
    @JoinTable(
        name = "page_tags",
        joinColumns = [JoinColumn(name = "page_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    var tags: MutableSet<Tag> = mutableSetOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
