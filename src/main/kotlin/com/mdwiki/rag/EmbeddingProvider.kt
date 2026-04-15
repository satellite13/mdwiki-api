package com.mdwiki.rag

interface EmbeddingProvider {
    fun embed(texts: List<String>): List<FloatArray>
    fun embed(text: String): FloatArray = embed(listOf(text)).first()
    fun dimension(): Int
}
