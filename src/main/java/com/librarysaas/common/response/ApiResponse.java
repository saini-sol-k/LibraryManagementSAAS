package com.librarysaas.common.response;

/**
 * Standard API response wrapper for all REST endpoints.
 * 
 * Success example:
 * {
 *   "success": true,
 *   "message": "Operation successful",
 *   "data": { ... },
 *   "errorCode": null
 * }
 * 
 * Error example:
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "data": { "field": "error message" },
 *   "errorCode": "VALIDATION_ERROR"
 * }
 */
public record ApiResponse<T>(boolean success, String message, T data, String errorCode) {
    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, null);
    }
}
