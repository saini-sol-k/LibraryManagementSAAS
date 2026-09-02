package com.librarysaas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components())
            .info(new Info().title("Library & Study Center SaaS API").version("v1")
                .description("Multi-tenant Library & Study Center Business Management SaaS"));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("400", new ApiResponse().description("Invalid request or validation failure"));
            operation.getResponses().addApiResponse("401", new ApiResponse().description("Authentication required or credentials invalid"));
            operation.getResponses().addApiResponse("403", new ApiResponse().description("Permission denied"));
            operation.getResponses().addApiResponse("404", new ApiResponse().description("Requested resource not found"));
            operation.getResponses().addApiResponse("409", new ApiResponse().description("Request conflicts with existing data"));
            operation.getResponses().addApiResponse("500", new ApiResponse().description("Unexpected server error"));
            return operation;
        };
    }
}
