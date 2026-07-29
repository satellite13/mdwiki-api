package com.mdwiki.controller

import com.mdwiki.dto.ApiErrorResponse
import com.mdwiki.error.AppException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.ErrorResponseException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletRequest

@RestControllerAdvice(basePackages = ["com.mdwiki.controller"])
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    @ExceptionHandler(AppException::class)
    fun handleAppException(e: AppException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        if (e.status.is5xxServerError) {
            log.error("AppException at {}: {}", request.requestURI, e.message, e)
        } else {
            log.warn("AppException at {}: {} ({})", request.requestURI, e.message, e.errorCode)
        }
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
        log.warn("Resource not found at {}: {}", request.requestURI, e.message)
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
        log.warn("Bad request at {}: {}", request.requestURI, e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = "BAD_REQUEST",
                    message = e.message ?: "Bad request",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("Illegal state at {}: {}", request.requestURI, e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    error = "CONFLICT",
                    message = e.message ?: "Operation conflicts with current state",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("Type mismatch at {}: parameter '{}' value '{}'", request.requestURI, e.name, e.value)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Invalid value for parameter '${e.name}'",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("Validation error at {}: {}", request.requestURI, e.message)
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
        log.warn("Invalid JSON at {}: {}", request.requestURI, e.message)
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
        log.error("Data integrity violation at {}: {}", request.requestURI, e.message, e)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    error = "CONFLICT",
                    message = e.mostSpecificCause.message ?: "Data integrity violation",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(e: MaxUploadSizeExceededException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("Upload too large at {}: {}", request.requestURI, e.message)
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(
                ApiErrorResponse(
                    error = "UPLOAD_TOO_LARGE",
                    message = "Uploaded file is too large. Please use a smaller file.",
                    path = request.requestURI
                )
            )
    }

    /** Spring-исключения с собственным статусом (ResponseStatusException, 405, missing params и т.п.). */
    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponse(e: ErrorResponseException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        if (status.is5xxServerError) {
            log.error("Error response at {}: {}", request.requestURI, e.message, e)
        } else {
            log.warn("Error response at {}: {} ({})", request.requestURI, e.message, status)
        }
        return ResponseEntity.status(e.statusCode)
            .body(
                ApiErrorResponse(
                    error = status.name,
                    message = e.message ?: status.reasonPhrase,
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.error("Unexpected error at {}: {}", request.requestURI, e.message, e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    error = "INTERNAL_ERROR",
                    message = "Unexpected server error",
                    path = request.requestURI
                )
            )
    }

}
