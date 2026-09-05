package com.mdwiki.service

import com.mdwiki.model.PageRevision
import com.mdwiki.model.RevisionOperation

data class RevisionMutation(val operation: RevisionOperation, val restoredFrom: PageRevision? = null)

object RevisionMutationContext {
    private val current = ThreadLocal<RevisionMutation?>()

    fun get(): RevisionMutation? = current.get()

    fun <T> with(mutation: RevisionMutation, block: () -> T): T {
        val previous = current.get()
        current.set(mutation)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }
}
