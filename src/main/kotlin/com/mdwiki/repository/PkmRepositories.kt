package com.mdwiki.repository

import com.mdwiki.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface UserPkmSettingsRepository : JpaRepository<UserPkmSettings, UUID>

interface UserDailyNoteRepository : JpaRepository<UserDailyNote, UserDailyNoteId> {
    fun findByUserIdAndNoteDate(userId: UUID, noteDate: LocalDate): UserDailyNote?
}

interface UserRecentPageRepository : JpaRepository<UserRecentPage, UserPageId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO user_recent_pages(user_id,page_id,last_opened_at,open_count)
            VALUES (:userId,:pageId,now(),1)
            ON CONFLICT(user_id,page_id) DO UPDATE
              SET last_opened_at=now(), open_count=user_recent_pages.open_count+1
        """,
        nativeQuery = true
    )
    fun touch(@Param("userId") userId: UUID, @Param("pageId") pageId: UUID)

    @Query(
        "select r from UserRecentPage r join fetch r.page p " +
            "where r.userId=:userId and p.deletedAt is null order by r.lastOpenedAt desc"
    )
    fun listActive(@Param("userId") userId: UUID): List<UserRecentPage>
}

interface UserFavoritePageRepository : JpaRepository<UserFavoritePage, UserPageId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO user_favorite_pages(user_id,page_id,created_at)
            VALUES (:userId,:pageId,now()) ON CONFLICT(user_id,page_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun add(@Param("userId") userId: UUID, @Param("pageId") pageId: UUID)

    fun deleteByUserIdAndPageId(userId: UUID, pageId: UUID)
    fun existsByUserIdAndPageId(userId: UUID, pageId: UUID): Boolean

    @Query(
        "select f from UserFavoritePage f join fetch f.page p " +
            "where f.userId=:userId and p.deletedAt is null order by f.createdAt desc"
    )
    fun listActive(@Param("userId") userId: UUID): List<UserFavoritePage>
}

interface UserFavoriteSearchRepository : JpaRepository<UserFavoriteSearch, UserSavedSearchId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO user_favorite_searches(user_id,saved_search_id,created_at)
            VALUES (:userId,:savedSearchId,now())
            ON CONFLICT(user_id,saved_search_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun add(@Param("userId") userId: UUID, @Param("savedSearchId") savedSearchId: UUID)

    fun deleteByUserIdAndSavedSearchId(userId: UUID, savedSearchId: UUID)
    fun existsByUserIdAndSavedSearchId(userId: UUID, savedSearchId: UUID): Boolean

    @Query(
        "select f from UserFavoriteSearch f join fetch f.savedSearch s " +
            "where f.userId=:userId order by f.createdAt desc"
    )
    fun listByUser(@Param("userId") userId: UUID): List<UserFavoriteSearch>
}

interface UserFavoriteViewRepository : JpaRepository<UserFavoriteView, UserSavedViewId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO user_favorite_views(user_id,view_id,created_at)
            VALUES (:userId,:viewId,now()) ON CONFLICT(user_id,view_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun add(@Param("userId") userId: UUID, @Param("viewId") viewId: UUID)

    fun deleteByUserIdAndViewId(userId: UUID, viewId: UUID)
    fun existsByUserIdAndViewId(userId: UUID, viewId: UUID): Boolean

    @Query(
        "select f from UserFavoriteView f join fetch f.view v " +
            "where f.userId=:userId order by f.createdAt desc"
    )
    fun listByUser(@Param("userId") userId: UUID): List<UserFavoriteView>
}
