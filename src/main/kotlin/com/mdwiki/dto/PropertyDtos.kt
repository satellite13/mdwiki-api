package com.mdwiki.dto

import com.fasterxml.jackson.databind.JsonNode
import com.mdwiki.model.PropertyType
import com.mdwiki.model.SavedViewFilterMode
import com.mdwiki.model.SavedViewType
import java.time.Instant
import java.util.UUID

data class PropertyDefinitionWriteRequest(
    val key: String,
    val displayName: String,
    val type: PropertyType,
    val config: JsonNode? = null,
    val required: Boolean = false,
    val expectedVersion: Long? = null
)
data class PropertyDefinitionResponse(
    val id: UUID, val key: String, val displayName: String, val type: PropertyType,
    val config: JsonNode, val required: Boolean, val version: Long, val createdAt: Instant, val updatedAt: Instant
)
data class PropertyOperation(val op: PropertyOperationType, val key: String, val value: JsonNode? = null)
enum class PropertyOperationType { SET, REMOVE }
data class PatchPagePropertiesRequest(val expectedUpdatedAt: Instant, val operations: List<PropertyOperation>)
data class PagePropertiesResponse(
    val definitions: List<PropertyDefinitionResponse>,
    /** Plain JSON values (string/number/bool/list/object), never raw Jackson node beans. */
    val values: Map<String, Any?>,
    val unknown: Map<String, Any?>,
    val warnings: List<String> = emptyList()
)
data class SavedViewWriteRequest(
    val name: String,
    val type: SavedViewType,
    val filterMode: SavedViewFilterMode = SavedViewFilterMode.ALL,
    /** Plain JSON; Jackson 3 cannot bind request bodies into jackson-2 JsonNode. */
    val filters: List<Map<String, Any?>> = emptyList(),
    val sort: List<Map<String, Any?>> = emptyList(),
    val grouping: Map<String, Any?>? = null,
    val layout: Map<String, Any?> = emptyMap(),
    val expectedVersion: Long? = null
)
data class SavedViewResponse(
    val id: UUID,
    val name: String,
    val type: SavedViewType,
    val filterMode: SavedViewFilterMode,
    val filters: List<Any?>,
    val sort: List<Any?>,
    val grouping: Map<String, Any?>?,
    val layout: Map<String, Any?>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val favorited: Boolean = false
)

/** One database-filtered view row. `groupKey` is present only for grouped views. */
data class ViewRunItem(
    val page: PageListItem,
    val groupKey: String? = null
)
