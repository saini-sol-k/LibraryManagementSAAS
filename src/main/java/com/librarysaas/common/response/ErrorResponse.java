package com.librarysaas.common.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error response model used by the global exception handler.
 * Implemented as a regular class for compatibility with IDEs/builds that
 * may not have Java record support configured.
 */
public class ErrorResponse {
	private final OffsetDateTime timestamp;
	private final int status;
	private final String code;
	private final String message;
	private final String path;
	private final List<String> errors;

	public ErrorResponse(OffsetDateTime timestamp, int status, String code, String message, String path, List<String> errors) {
		this.timestamp = timestamp;
		this.status = status;
		this.code = code;
		this.message = message;
		this.path = path;
		this.errors = errors;
	}

	public OffsetDateTime getTimestamp() {
		return timestamp;
	}

	public int getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public String getPath() {
		return path;
	}

	public List<String> getErrors() {
		return errors;
	}
}
