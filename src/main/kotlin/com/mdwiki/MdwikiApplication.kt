package com.mdwiki

import com.mdwiki.config.EmbeddingProperties
import com.mdwiki.config.JwtProperties
import com.mdwiki.config.WikiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, WikiProperties::class, EmbeddingProperties::class)
class MdwikiApplication

fun main(args: Array<String>) {
    runApplication<MdwikiApplication>(*args)
}
