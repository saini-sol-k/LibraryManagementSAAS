package com.librarysaas.common.exception;

/**
 * Thrown when a business rule is violated.
 * Examples: Organization is inactive, user not a member, etc.
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String message) {
        this(message, null);
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
