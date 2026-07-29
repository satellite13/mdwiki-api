package com.mdwiki.controller

import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class VersionResponse(
    val name: String,
    val version: String,
    val gitSha: String
)

@RestController
@RequestMapping("/api/version")
class VersionController(
    private val buildProperties: BuildProperties
) {

    @GetMapping
    fun version(): VersionResponse = VersionResponse(
        name = buildProperties.name?.takeIf { it.isNotBlank() } ?: "mdwiki-api",
        version = buildProperties.version?.takeIf { it.isNotBlank() } ?: "0.1.0",
        gitSha = buildProperties.get("gitSha")?.takeIf { it.isNotBlank() } ?: "unknown"
    )
}
