package com.mdwiki.error

import org.springframework.http.HttpStatus

class BadRequestException(message: String) : AppException(
    errorCode = "BAD_REQUEST",
    status = HttpStatus.BAD_REQUEST,
    message = message
)
