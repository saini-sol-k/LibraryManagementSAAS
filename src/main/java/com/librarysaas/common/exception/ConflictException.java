package com.librarysaas.common.exception;

/**
 * Thrown when there is a conflict (e.g., duplicate student code).
 * Returns HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    private final String errorCode;

    public ConflictException(String message) {
        this(message, "CONFLICT");
    }

    public ConflictException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
