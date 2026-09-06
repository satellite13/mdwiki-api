package com.mdwiki.dto

data class AnswerRequest(val question: String, val topK: Int = 5)

data class AnswerCitation(
    val id: Int,
    val pageSlug: String,
    val pageTitle: String,
    val sectionKey: String?,
    val sectionHeading: String?,
    val quote: String,
    val score: Double
)

data class AnswerResponse(
    val answerMd: String,
    val citations: List<AnswerCitation>,
    val grounded: Boolean,
    val model: String = "extractive-rag"
)
