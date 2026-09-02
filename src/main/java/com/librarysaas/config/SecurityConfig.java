package com.librarysaas.config;

import com.librarysaas.security.jwt.JwtAuthenticationFilter;
import com.librarysaas.security.ApiAccessDeniedHandler;
import com.librarysaas.security.jwt.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    // Allow these to be optional for slice tests that don't load JWT beans
    public SecurityConfig(org.springframework.beans.factory.ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider,
                          org.springframework.beans.factory.ObjectProvider<JwtAuthenticationEntryPoint> authenticationEntryPointProvider,
                          org.springframework.beans.factory.ObjectProvider<ApiAccessDeniedHandler> accessDeniedHandlerProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilterProvider.getIfAvailable();
        this.authenticationEntryPoint = authenticationEntryPointProvider.getIfAvailable();
        this.accessDeniedHandler = accessDeniedHandlerProvider.getIfAvailable();
    }

    // Password encoder used for verifying stored BCrypt hashes
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Security filter chain using JWT authentication
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: there is no browser session or CSRF token to protect,
            // and no cookie for a cross-site request to ride on.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authentication happens only through the JWT filter below.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        if (authenticationEntryPoint != null || accessDeniedHandler != null) {
            http.exceptionHandling(e -> {
                if (authenticationEntryPoint != null) {
                    e.authenticationEntryPoint(authenticationEntryPoint);
                }
                if (accessDeniedHandler != null) {
                    e.accessDeniedHandler(accessDeniedHandler);
                }
            });
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .anyRequest().authenticated()
        );

        if (jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
