package com.mdwiki.controller

import com.mdwiki.service.SyncService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sync")
class SyncController(private val syncService: SyncService) {

    @PostMapping
    fun sync(): SyncService.SyncResult = syncService.fullSync()

    @PostMapping("/reindex")
    fun reindex(): SyncService.ReindexResult = syncService.reindexAll()
}
