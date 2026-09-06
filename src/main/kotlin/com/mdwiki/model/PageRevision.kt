package com.mdwiki.model

import com.mdwiki.util.PersistentInstant
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class RevisionOperation {
    CREATE, EDIT, PATCH, RESTORE, IMPORT, FILESYSTEM, RENAME, DELETE, RESTORE_TRASH
}

@Entity
@Table(
    name = "page_revisions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["page_id", "revision_no"])]
)
class PageRevision(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "page_id", nullable = false)
    val pageId: UUID,

    @Column(name = "revision_no", nullable = false)
    val revisionNo: Long,

    @Column(name = "content_md", nullable = false, columnDefinition = "text")
    val contentMd: String,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    val contentHash: String,

    @Column(name = "title_snapshot", nullable = false, length = 500)
    val titleSnapshot: String,

    @Column(name = "slug_snapshot", nullable = false, length = 500)
    val slugSnapshot: String,

    @Column(name = "folder_id_snapshot")
    val folderIdSnapshot: UUID?,

    @Column(name = "deleted_at_snapshot")
    val deletedAtSnapshot: Instant?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val operation: RevisionOperation,

    @Column(name = "created_by_user_id")
    val createdByUserId: UUID?,

    @Column(name = "created_by_name")
    val createdByName: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = PersistentInstant.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restored_from_revision_id")
    val restoredFrom: PageRevision? = null
)
