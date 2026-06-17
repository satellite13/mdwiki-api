package com.mdwiki.util

import java.text.Collator
import java.util.Locale

/** Сравнение строк с учётом чисел: «Глава 9» перед «Глава 10». */
object NaturalSort {
    private val chunkPattern = Regex("""\d+|\D+""")
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply {
        strength = Collator.PRIMARY
    }

    fun compare(a: String, b: String): Int {
        val aChunks = chunkPattern.findAll(a).map { it.value }.toList()
        val bChunks = chunkPattern.findAll(b).map { it.value }.toList()
        val limit = minOf(aChunks.size, bChunks.size)

        for (i in 0 until limit) {
            val left = aChunks[i]
            val right = bChunks[i]
            val cmp = when {
                left.all(Char::isDigit) && right.all(Char::isDigit) -> {
                    val leftNum = left.trimStart('0').ifEmpty { "0" }.toBigInteger()
                    val rightNum = right.trimStart('0').ifEmpty { "0" }.toBigInteger()
                    leftNum.compareTo(rightNum)
                }
                else -> collator.compare(left, right)
            }
            if (cmp != 0) return cmp
        }

        return aChunks.size.compareTo(bChunks.size)
    }
}
