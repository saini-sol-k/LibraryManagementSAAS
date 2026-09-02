package com.librarysaas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token issued by POST /api/auth/login. "
                            + "Send as: Authorization: Bearer <token>")))
            // Every endpoint except /api/auth/** requires a bearer token (see SecurityConfig).
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .info(new Info().title("Library & Study Center SaaS API").version("v1")
                .description("Multi-tenant Library & Study Center Business Management SaaS.\n\n"
                        + "All responses use a common envelope: "
                        + "`{ \"success\": boolean, \"message\": string, \"data\": object|null, "
                        + "\"errorCode\": string|null }`.\n\n"
                        + "Tenant scope is resolved server-side from the authenticated user's "
                        + "primary organization/library. The optional `X-Library-Id` header is only "
                        + "a fallback and is never trusted in place of membership validation."));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("400", errorResponse(
                    "Invalid request or validation failure",
                    "Validation failed", "VALIDATION_ERROR"));
            operation.getResponses().addApiResponse("401", errorResponse(
                    "Authentication required or credentials invalid",
                    "Authentication is required", "UNAUTHORIZED"));
            operation.getResponses().addApiResponse("403", errorResponse(
                    "Permission denied, or the resource belongs to another tenant",
                    "You do not have permission to perform this operation", "FORBIDDEN"));
            operation.getResponses().addApiResponse("404", errorResponse(
                    "Requested resource not found",
                    "Student not found", "STUDENT_NOT_FOUND"));
            operation.getResponses().addApiResponse("409", errorResponse(
                    "Request conflicts with existing data",
                    "Library code already exists in this organization", "LIBRARY_CODE_ALREADY_EXISTS"));
            operation.getResponses().addApiResponse("500", errorResponse(
                    "Unexpected server error",
                    "Unable to process the request. Please try again later.", "INTERNAL_ERROR"));
            return operation;
        };
    }

    /**
     * Documents an error using the same envelope the GlobalExceptionHandler actually returns,
     * so the published contract cannot drift from the implementation.
     */
    private static ApiResponse errorResponse(String description, String message, String errorCode) {
        Schema<?> envelope = new ObjectSchema()
                .addProperty("success", new BooleanSchema()._default(Boolean.FALSE).example(Boolean.FALSE))
                .addProperty("message", new StringSchema().example(message))
                .addProperty("data", new ObjectSchema().nullable(true)
                        .description("Field-level errors for validation failures; null otherwise"))
                .addProperty("errorCode", new StringSchema().example(errorCode));

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(envelope)));
    }
}
