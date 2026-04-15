package com.mdwiki.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mdwiki")
data class WikiProperties(
    val contentDir: String = "./wiki-content"
)
