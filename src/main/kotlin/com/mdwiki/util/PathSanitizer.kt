package com.mdwiki.util

/**
 * Единая санация имён сегментов файлового пути для wiki-контента.
 * Раньше существовало три расходящихся копии (WikiFileService, SyncService, WikiSyncEngine),
 * что грозило рассинхроном путей «БД ↔ диск».
 */
object PathSanitizer {

    /** Безопасное имя сегмента пути: без разделителей, непустое. */
    fun sanitizePathSegment(input: String): String {
        val cleaned = input
            .trim()
            .replace('/', '-')
            .replace('\\', '-')
        return if (cleaned.isBlank()) "folder" else cleaned
    }
}
