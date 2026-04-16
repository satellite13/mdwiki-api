package com.mdwiki.error

import org.springframework.http.HttpStatus

open class AppException(
    val errorCode: String,
    val status: HttpStatus,
    message: String
) : RuntimeException(message)
