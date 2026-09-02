package com.librarysaas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    public static final String HEADER_ORG = "X-Organization-Id";
    public static final String HEADER_LIB = "X-Library-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String orgHeader = request.getHeader(HEADER_ORG);
            String libHeader = request.getHeader(HEADER_LIB);

            if (TenantContext.getOrganizationId() == null && orgHeader != null && !orgHeader.isBlank()) {
                try {
                    TenantContext.setOrganizationId(Long.parseLong(orgHeader));
                } catch (NumberFormatException e) {
                    log.warn("Invalid {} header value: {}", HEADER_ORG, orgHeader);
                }
            }

            if (TenantContext.getLibraryId() == null && libHeader != null && !libHeader.isBlank()) {
                try {
                    TenantContext.setLibraryId(Long.parseLong(libHeader));
                } catch (NumberFormatException e) {
                    log.warn("Invalid {} header value: {}", HEADER_LIB, libHeader);
                }
            }

            // Future: if authentication contains tenant claims, extract them here.

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
