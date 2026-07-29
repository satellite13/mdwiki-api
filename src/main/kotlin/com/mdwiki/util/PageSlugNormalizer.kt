package com.mdwiki.util

/**
 * Каноническая нормализация slug/title страницы: lowercase, trim, небуквенно-цифровые
 * последовательности → '-', обрезка '-' по краям. Совпадает с SQL-выражением
 * `trim(both '-' from regexp_replace(lower(trim(x)), '[^a-z0-9а-яё]+', '-', 'g'))`,
 * которым заполняется pages.normalized_title в миграции 002.
 */
object PageSlugNormalizer {
    private val slugNonAlnum = Regex("[^a-z0-9а-яё]+", RegexOption.IGNORE_CASE)
    private val slugTrimDashes = Regex("^-+|-+$")

    fun normalize(raw: String): String =
        raw.lowercase().trim()
            .replace(slugNonAlnum, "-")
            .replace(slugTrimDashes, "")
}
