package com.mdwiki.error

import org.springframework.http.HttpStatus

class ConflictException(message: String) : AppException(
    errorCode = "CONFLICT",
    status = HttpStatus.CONFLICT,
    message = message
)
