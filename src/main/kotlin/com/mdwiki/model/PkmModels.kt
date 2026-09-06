package com.mdwiki.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.domain.Persistable

@Entity
@Table(name = "user_pkm_settings")
class UserPkmSettings(
    @Id
    @Column(name = "user_id")
    val userId: UUID,
    @OneToOne(fetch = FetchType.LAZY) @MapsId @JoinColumn(name = "user_id")
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inbox_folder_id")
    var inboxFolder: Folder? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "daily_folder_id")
    var dailyFolder: Folder? = null,
    @Column(nullable = false, length = 64)
    var timezone: String = "UTC",
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) : Persistable<UUID> {
    @Transient
    private var newEntity: Boolean = true

    override fun getId(): UUID = userId
    override fun isNew(): Boolean = newEntity

    @PostLoad
    @PostPersist
    private fun markPersisted() {
        newEntity = false
    }
}

data class UserPageId(var userId: UUID? = null, var pageId: UUID? = null) : Serializable
data class UserSavedSearchId(var userId: UUID? = null, var savedSearchId: UUID? = null) : Serializable
data class UserSavedViewId(var userId: UUID? = null, var viewId: UUID? = null) : Serializable
data class UserDailyNoteId(var userId: UUID? = null, var noteDate: LocalDate? = null) : Serializable

@Entity
@Table(name = "user_daily_notes")
@IdClass(UserDailyNoteId::class)
class UserDailyNote(
    @Id @Column(name = "user_id") val userId: UUID,
    @Id @Column(name = "note_date") val noteDate: LocalDate,
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "page_id", nullable = false, unique = true)
    val page: Page,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_recent_pages")
@IdClass(UserPageId::class)
class UserRecentPage(
    @Id @Column(name = "user_id") val userId: UUID,
    @Id @Column(name = "page_id") val pageId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "page_id", insertable = false, updatable = false)
    val page: Page,
    @Column(name = "last_opened_at", nullable = false)
    var lastOpenedAt: Instant = Instant.now(),
    @Column(name = "open_count", nullable = false)
    var openCount: Long = 1
)

@Entity
@Table(name = "user_favorite_pages")
@IdClass(UserPageId::class)
class UserFavoritePage(
    @Id @Column(name = "user_id") val userId: UUID,
    @Id @Column(name = "page_id") val pageId: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "page_id", insertable = false, updatable = false)
    val page: Page,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_favorite_searches")
@IdClass(UserSavedSearchId::class)
class UserFavoriteSearch(
    @Id @Column(name = "user_id") val userId: UUID,
    @Id @Column(name = "saved_search_id") val savedSearchId: UUID,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_search_id", insertable = false, updatable = false)
    val savedSearch: SavedSearch,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_favorite_views")
@IdClass(UserSavedViewId::class)
class UserFavoriteView(
    @Id @Column(name = "user_id") val userId: UUID,
    @Id @Column(name = "view_id") val viewId: UUID,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "view_id", insertable = false, updatable = false)
    val view: SavedView,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
