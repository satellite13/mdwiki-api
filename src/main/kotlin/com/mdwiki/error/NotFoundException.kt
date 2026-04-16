package com.mdwiki.error

import org.springframework.http.HttpStatus

class NotFoundException(message: String) : AppException(
    errorCode = "NOT_FOUND",
    status = HttpStatus.NOT_FOUND,
    message = message
)
