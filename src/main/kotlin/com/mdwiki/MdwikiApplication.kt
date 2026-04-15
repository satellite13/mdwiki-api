package com.mdwiki

import com.mdwiki.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class MdwikiApplication

fun main(args: Array<String>) {
    runApplication<MdwikiApplication>(*args)
}
