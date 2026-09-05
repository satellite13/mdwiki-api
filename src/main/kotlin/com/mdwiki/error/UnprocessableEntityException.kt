package com.mdwiki.error

import org.springframework.http.HttpStatus

class UnprocessableEntityException(message: String) : AppException(
    errorCode = "UNPROCESSABLE_ENTITY",
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    message = message
)
