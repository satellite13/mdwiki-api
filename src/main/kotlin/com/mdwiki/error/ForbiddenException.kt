package com.mdwiki.error

import org.springframework.http.HttpStatus

class ForbiddenException(message: String) : AppException(
    errorCode = "FORBIDDEN",
    status = HttpStatus.FORBIDDEN,
    message = message
)
