package com.librarysaas.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.librarysaas.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionReturnsSafeInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAll(
                new RuntimeException("java.sql.SQLException: password=secret SQL SELECT * FROM users"),
                new MockHttpServletRequest("GET", "/api/test-failure"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Unable to process the request. Please try again later.");
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");

        String serializedResponse = response.getBody().toString();
        assertThat(serializedResponse).doesNotContain("java.", "Exception", "SQL", "password", "secret");
    }

    @Test
    void noResourceFoundIsReportedAsNotFoundNotInternalError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedRequest(
                new NoResourceFoundException(HttpMethod.GET, "/api/students/1/none"),
                new MockHttpServletRequest("GET", "/api/students/1/none"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("The requested resource was not found");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unsupportedHttpMethodIsReportedAsMethodNotAllowedNotInternalError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedRequest(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PUT", "DELETE")),
                new MockHttpServletRequest("POST", "/api/students/1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedMediaTypeIsReportedAsUnsupportedMediaTypeNotInternalError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedRequest(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                new MockHttpServletRequest("POST", "/api/students"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void studentNotFoundKeepsItsOwnErrorCodeAndReturns404() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"),
                new MockHttpServletRequest("GET", "/api/students/1001111"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Student not found");
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().errorCode()).isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    void forbiddenStillReturns403() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(
                new ForbiddenException("You do not have permission to perform this operation"),
                new MockHttpServletRequest("GET", "/api/students/4"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("FORBIDDEN");
    }
}
