package com.librarysaas.common.exception;

/**
 * Thrown when authentication is required but missing, invalid, or expired.
 * Returns HTTP 401 Unauthorized.
 */
public class UnauthorizedException extends RuntimeException {
    private final String errorCode;

    public UnauthorizedException(String message) {
        this(message, "UNAUTHORIZED");
    }

    public UnauthorizedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
