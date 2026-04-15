package com.mdwiki

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MdwikiApplication

fun main(args: Array<String>) {
    runApplication<MdwikiApplication>(*args)
}
