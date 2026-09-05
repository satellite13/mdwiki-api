package com.mdwiki.model

import com.fasterxml.jackson.databind.JsonNode
import com.mdwiki.util.PersistentInstant
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PropertyType { TEXT, NUMBER, BOOLEAN, DATE, DATETIME, URL, SELECT, MULTI_SELECT, PAGE_REF }
enum class SavedViewType { TABLE, LIST, CARDS }

@Entity
@Table(name = "property_definitions")
class PropertyDefinition(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    @Column(nullable = false, length = 100) val key: String,
    @Column(name = "display_name", nullable = false, length = 120) var displayName: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) val type: PropertyType,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var config: JsonNode,
    @Column(nullable = false) var required: Boolean = false,
    @Column(nullable = false) var version: Long = 1,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") val createdBy: User? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = PersistentInstant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = PersistentInstant.now(),
    @Column(name = "deleted_at") var deletedAt: Instant? = null
)

@Entity
@Table(name = "page_property_values")
class PagePropertyValue(
    @EmbeddedId val id: PagePropertyValueId,
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("pageId") @JoinColumn(name = "page_id") val page: Page,
    @ManyToOne(fetch = FetchType.LAZY) @MapsId("propertyId") @JoinColumn(name = "property_id") val property: PropertyDefinition,
    @Column(name = "source_content_hash", nullable = false, length = 64) var sourceContentHash: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "value_json", nullable = false, columnDefinition = "jsonb") var valueJson: JsonNode,
    @Column(name = "text_value") var textValue: String? = null,
    @Column(name = "number_value") var numberValue: BigDecimal? = null,
    @Column(name = "bool_value") var boolValue: Boolean? = null,
    @Column(name = "date_value") var dateValue: LocalDate? = null,
    @Column(name = "datetime_value") var datetimeValue: Instant? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "page_ref_id") var pageRef: Page? = null
)

@Embeddable
data class PagePropertyValueId(
    @Column(name = "page_id") val pageId: UUID = UUID(0, 0),
    @Column(name = "property_id") val propertyId: UUID = UUID(0, 0)
) : java.io.Serializable

@Entity
@Table(name = "saved_views")
class SavedView(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) val user: User,
    @Column(nullable = false, length = 120) var name: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) var type: SavedViewType,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var filters: JsonNode,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var sort: JsonNode,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var grouping: JsonNode? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var layout: JsonNode,
    @Column(nullable = false) var version: Long = 1,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = PersistentInstant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = PersistentInstant.now()
)
