package com.mdwiki.repository

import java.util.UUID

interface TagWithPageCountView {
    fun getId(): UUID
    fun getName(): String
    fun getPageCount(): Long
}
