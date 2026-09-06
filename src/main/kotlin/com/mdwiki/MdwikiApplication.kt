package com.mdwiki

import com.mdwiki.config.EmbeddingProperties
import com.mdwiki.config.JwtProperties
import com.mdwiki.config.WikiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableConfigurationProperties(JwtProperties::class, WikiProperties::class, EmbeddingProperties::class)
@EnableScheduling
class MdwikiApplication

fun main(args: Array<String>) {
    runApplication<MdwikiApplication>(*args)
}
