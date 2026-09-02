package com.librarysaas.security.jwt;

import com.librarysaas.security.TenantContext;
import com.librarysaas.security.model.User;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserTenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
@ConditionalOnBean(JwtTokenProvider.class)
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserDetailsService userDetailsService, UserRepository userRepository, UserTenantRepository userTenantRepository) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.userTenantRepository = userTenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (tokenProvider.validateToken(token)) {
                    String username = tokenProvider.getUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Resolve tenant information authoritative from server-side associations
                    Optional<User> uopt = userRepository.findByUsernameOrEmail(username);
                    if (uopt.isPresent()) {
                        User u = uopt.get();
                        userTenantRepository.findPrimaryOrganizationId(u.getUserId()).ifPresent(TenantContext::setOrganizationId);
                        userTenantRepository.findPrimaryLibraryId(u.getUserId()).ifPresent(TenantContext::setLibraryId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to process JWT authentication: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Do not clear TenantContext here; TenantFilter will clear at end of request chain.
        }
    }
}
