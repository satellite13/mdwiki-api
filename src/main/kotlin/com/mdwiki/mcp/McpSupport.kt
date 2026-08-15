package com.mdwiki.mcp

import com.mdwiki.dto.AttachmentResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/** Общие хелперы MCP-инструментов (ранее дублировались в каждом tool-классе). */
object McpSupport {

    fun parseInstant(raw: String): Instant {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Invalid ISO-8601 instant: $raw")
        }
    }

    fun parseUuid(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID: $raw")
        }
    }

    fun currentUsername(): String {
        return SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("Not authenticated")
    }

    fun attachmentToMap(attachment: AttachmentResponse): Map<String, Any?> = mapOf(
        "id" to attachment.id.toString(),
        "originalName" to attachment.originalName,
        "storedName" to attachment.storedName,
        "contentType" to attachment.contentType,
        "sizeBytes" to attachment.sizeBytes,
        "uploadedBy" to attachment.uploadedBy,
        "pageId" to attachment.pageId?.toString(),
        "url" to attachment.url,
        "createdAt" to attachment.createdAt.toString()
    )
}
