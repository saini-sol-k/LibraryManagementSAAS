package com.librarysaas.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class AuditConfig {

    // Minimal AuditorAware that returns empty. Replace later with authenticated user id.
    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> Optional.empty();
    }
}
