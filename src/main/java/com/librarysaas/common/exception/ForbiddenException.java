package com.librarysaas.common.exception;

/**
 * Thrown when authenticated user lacks permission for the operation.
 * Returns HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {
    private final String errorCode;

    public ForbiddenException(String message) {
        this(message, "FORBIDDEN");
    }

    public ForbiddenException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
