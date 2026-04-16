package com.mdwiki.controller

import com.mdwiki.dto.ApiErrorResponse
import com.mdwiki.error.AppException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletRequest

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(AppException::class)
    fun handleAppException(e: AppException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(e.status)
            .body(
                ApiErrorResponse(
                    error = e.errorCode,
                    message = e.message ?: "Application error",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiErrorResponse(
                    error = "NOT_FOUND",
                    message = e.message ?: "Resource not found",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = "BAD_REQUEST",
                    message = e.message ?: "Bad request",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = "VALIDATION_ERROR",
                    message = message,
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadJson(e: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = "BAD_REQUEST",
                    message = e.mostSpecificCause.message ?: "Invalid request body",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    error = "CONFLICT",
                    message = e.mostSpecificCause.message ?: "Data integrity violation",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.error("Unhandled exception on ${request.method} ${request.requestURI}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    error = "INTERNAL_ERROR",
                    message = e.message ?: "Unexpected server error",
                    path = request.requestURI
                )
            )
    }
}
