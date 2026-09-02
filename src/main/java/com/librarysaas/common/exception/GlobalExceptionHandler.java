package com.librarysaas.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.librarysaas.common.response.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for REST API.
 * 
 * All error responses follow the ApiResponse format:
 * {
 *   "success": false,
 *   "message": "Human-readable error message",
 *   "data": null or { field-specific errors },
 *   "errorCode": "SPECIFIC_ERROR_CODE"
 * }
 * 
 * Server-side logging includes full exception details for debugging.
 * Client responses never expose stack traces, SQL, or sensitive details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .collect(Collectors.toMap(
                    error -> {
                        if (error instanceof FieldError fe) {
                            return fe.getField();
                        }
                        return error.getObjectName();
                    },
                    error -> error.getDefaultMessage(),
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));

        log.debug("Validation error on {}: {}", request.getRequestURI(), fieldErrors);
        
        ApiResponse<Map<String, String>> resp = new ApiResponse<>(
            false,
            "Validation failed",
            fieldErrors,
            "VALIDATION_ERROR"
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(resp);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.debug("Invalid request on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, "Invalid request data", null, "BAD_REQUEST"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        log.debug("Illegal argument on {}: {}", request.getRequestURI(), message);
        
        // Detect conflict-type errors (membership, duplicates, already exists)
        boolean isConflict = message.toLowerCase().contains("membership") || 
                             message.toLowerCase().contains("already exists") ||
                             message.toLowerCase().contains("duplicate");
        
        String errorCode = isConflict ? "MEMBERSHIP_NUMBER_ALREADY_EXISTS" : "INVALID_ARGUMENT";
        HttpStatus status = isConflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            message,
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(status)
            .body(resp);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity conflict on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, "Request conflicts with existing data", null, "CONFLICT"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "NOT_FOUND";
        log.debug("Resource not found on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(resp);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "CONFLICT";
        log.debug("Conflict on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(resp);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "DUPLICATE_RESOURCE";
        log.debug("Duplicate resource on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(resp);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "UNAUTHORIZED";
        log.debug("Unauthorized on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(resp);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "FORBIDDEN";
        log.debug("Forbidden on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(resp);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        log.debug("Business rule violation on {}: {} ({})", request.getRequestURI(), ex.getMessage(), errorCode);
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            ex.getMessage(),
            null,
            errorCode
        );
        
        // Use 400 or 409 depending on error code (default 400 for business rules)
        HttpStatus status = errorCode.contains("DUPLICATE") || errorCode.contains("CONFLICT") || errorCode.contains("EXISTS")
            ? HttpStatus.CONFLICT
            : HttpStatus.BAD_REQUEST;
        
        return ResponseEntity
            .status(status)
            .body(resp);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        
        log.debug("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            "You do not have permission to perform this operation",
            null,
            "FORBIDDEN"
        );
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(resp);
    }

    /**
     * Client errors raised by Spring MVC itself: no handler or static resource matches
     * the URL (404), the HTTP method is not allowed (405), or the media type is not
     * supported / not acceptable (415 / 406).
     *
     * Each of these carries its own status via {@link ErrorResponse}. Without this
     * handler they fall through to {@link #handleAll} and are reported as
     * INTERNAL_ERROR, which hides a client error behind a 500.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedRequest(
            Exception ex, HttpServletRequest request) {

        // All of the mapped types implement ErrorResponse and declare their own status.
        HttpStatus status = ex instanceof ErrorResponse errorResponse
                ? HttpStatus.valueOf(errorResponse.getStatusCode().value())
                : HttpStatus.BAD_REQUEST;

        String message = status == HttpStatus.NOT_FOUND
                ? "The requested resource was not found"
                : status.getReasonPhrase();

        log.debug("Unsupported request on {}: {} -> {}", request.getRequestURI(),
                ex.getClass().getSimpleName(), status.value());

        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            message,
            null,
            status.name()
        );

        return ResponseEntity
            .status(status)
            .body(resp);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {
        
        // Log full exception server-side for debugging
        log.error("Unexpected error on {}: {} - {}", request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        // Return generic safe message to client - never expose stack traces or details
        ApiResponse<Void> resp = new ApiResponse<>(
            false,
            "Unable to process the request. Please try again later.",
            null,
            "INTERNAL_ERROR"
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(resp);
    }
}
