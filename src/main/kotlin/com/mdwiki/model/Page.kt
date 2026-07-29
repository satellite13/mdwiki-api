package com.mdwiki.model

import com.fasterxml.jackson.databind.JsonNode
import com.mdwiki.util.PageSlugNormalizer
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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

    /** Нормализованный title для equality-поиска (заполняется хуком ниже и миграцией 002). */
    @Column(name = "normalized_title", length = 500)
    var normalizedTitle: String? = null,

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    var folder: Folder? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frontmatter_meta", columnDefinition = "jsonb")
    var frontmatterMeta: JsonNode? = null,

    @ManyToMany
    // Батч-подгрузка тегов: закрывает N+1 там, где нет EntityGraph (пагинированный findAll, BFS в GraphService).
    @BatchSize(size = 50)
    @JoinTable(
        name = "page_tags",
        joinColumns = [JoinColumn(name = "page_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    var tags: MutableSet<Tag> = mutableSetOf(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /** Держит normalized_title синхронным с title при любом INSERT/UPDATE через JPA. */
    @PrePersist
    @PreUpdate
    private fun syncNormalizedTitle() {
        normalizedTitle = PageSlugNormalizer.normalize(title)
    }
}
