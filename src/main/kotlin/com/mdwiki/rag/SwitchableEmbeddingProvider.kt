package com.mdwiki.rag

import java.util.concurrent.atomic.AtomicReference

class SwitchableEmbeddingProvider(initialProvider: EmbeddingProvider) : EmbeddingProvider {
    private val delegate = AtomicReference(initialProvider)

    fun switchTo(newProvider: EmbeddingProvider) {
        delegate.set(newProvider)
    }

    override fun embed(texts: List<String>): List<FloatArray> = delegate.get().embed(texts)

    override fun dimension(): Int = delegate.get().dimension()
}
