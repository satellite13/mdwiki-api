package com.mdwiki.service

import org.springframework.stereotype.Service

data class RevisionDiffRow(
    val kind: String,
    val before: String?,
    val after: String?
)

data class RevisionDiffResult(
    val rows: List<RevisionDiffRow>,
    val truncated: Boolean = false
)

/**
 * Stable, line-oriented diff contract. Empty terminal lines are significant.
 * LCS is bounded to avoid quadratic memory use for large documents.
 */
@Service
class RevisionDiffService {
    fun diff(before: String, after: String): RevisionDiffResult {
        val left = before.split('\n')
        val right = after.split('\n')
        if (left.size.toLong() * right.size.toLong() > MAX_LCS_CELLS) {
            return RevisionDiffResult(chunkFallback(left, right), truncated = true)
        }
        val lengths = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in left.indices.reversed()) {
            for (j in right.indices.reversed()) {
                lengths[i][j] = if (left[i] == right[j]) {
                    lengths[i + 1][j + 1] + 1
                } else {
                    maxOf(lengths[i + 1][j], lengths[i][j + 1])
                }
            }
        }
        val rows = mutableListOf<RevisionDiffRow>()
        var i = 0
        var j = 0
        while (i < left.size || j < right.size) {
            when {
                i < left.size && j < right.size && left[i] == right[j] -> {
                    rows += RevisionDiffRow("CONTEXT", left[i], right[j])
                    i++
                    j++
                }
                i < left.size && j < right.size &&
                    lengths[i + 1][j] == lengths[i][j + 1] -> {
                    rows += RevisionDiffRow("REMOVE", left[i++], null)
                    rows += RevisionDiffRow("ADD", null, right[j++])
                }
                j < right.size && (i == left.size || lengths[i][j + 1] >= lengths[i + 1][j]) -> {
                    rows += RevisionDiffRow("ADD", null, right[j++])
                }
                else -> rows += RevisionDiffRow("REMOVE", left[i++], null)
            }
        }
        return RevisionDiffResult(rows)
    }

    private fun chunkFallback(left: List<String>, right: List<String>): List<RevisionDiffRow> {
        val prefix = left.zip(right).takeWhile { it.first == it.second }.size
        val rows = left.take(prefix).map { RevisionDiffRow("CONTEXT", it, it) }.toMutableList()
        left.drop(prefix).take(FALLBACK_LINES).forEach { rows += RevisionDiffRow("REMOVE", it, null) }
        right.drop(prefix).take(FALLBACK_LINES).forEach { rows += RevisionDiffRow("ADD", null, it) }
        return rows
    }

    companion object {
        private const val MAX_LCS_CELLS = 4_000_000L
        private const val FALLBACK_LINES = 2_000
    }
}
