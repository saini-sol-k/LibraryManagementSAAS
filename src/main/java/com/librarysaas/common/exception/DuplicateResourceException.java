package com.librarysaas.common.exception;

/**
 * Thrown when attempting to create a resource that already exists.
 * Returns HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {
    private final String errorCode;

    public DuplicateResourceException(String message) {
        this(message, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
