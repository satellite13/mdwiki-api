package com.mdwiki.repository

import com.mdwiki.model.PageRevision
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PageRevisionRepository : JpaRepository<PageRevision, UUID> {
    @Query("""
        select r from PageRevision r
        where r.pageId = :pageId
          and (:before is null or r.revisionNo < :before)
        order by r.revisionNo desc
    """)
    fun list(
        @Param("pageId") pageId: UUID,
        @Param("before") before: Long?,
        pageable: Pageable
    ): List<PageRevision>

    fun findByPageIdAndRevisionNo(pageId: UUID, revisionNo: Long): PageRevision?

    fun findTopByPageIdOrderByRevisionNoDesc(pageId: UUID): PageRevision?

    @Query("select coalesce(max(r.revisionNo), 0) from PageRevision r where r.pageId = :pageId")
    fun maxRevisionNo(@Param("pageId") pageId: UUID): Long
}
