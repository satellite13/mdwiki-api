package com.mdwiki.error

import org.springframework.http.HttpStatus

class UnauthorizedException(message: String) : AppException(
    errorCode = "UNAUTHORIZED",
    status = HttpStatus.UNAUTHORIZED,
    message = message
)
