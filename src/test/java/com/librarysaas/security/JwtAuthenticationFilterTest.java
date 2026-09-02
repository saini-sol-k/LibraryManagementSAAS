package com.librarysaas.security;

import com.librarysaas.security.jwt.JwtProperties;
import com.librarysaas.security.jwt.JwtTokenProvider;
import com.librarysaas.security.jwt.JwtAuthenticationFilter;
import com.librarysaas.security.model.User;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserTenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwtAuthenticationFilterTest {

    @Test
    public void jwtFilterSetsTenantContextWhenValidToken() throws Exception {
        JwtProperties props = new JwtProperties() {
            @Override
            public String getSecret() { return "change-me-dev-secret-do-not-use-in-prod"; }

            @Override
            public long getAccessTokenExpirationSec() { return 3600L; }
        };

        JwtTokenProvider provider = new JwtTokenProvider(props);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "alice",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = provider.createToken(auth);

        UserDetailsService uds = mock(UserDetailsService.class);
        when(uds.loadUserByUsername("alice")).thenReturn(org.springframework.security.core.userdetails.User
                .withUsername("alice").password("").authorities("ROLE_USER").build());

        UserRepository userRepository = mock(UserRepository.class);
        // avoid mocking entity class to bypass Byte Buddy / Mockito inline issues on newer JDKs
        User realUser = new User() {
            @Override
            public Long getUserId() { return 42L; }
        };
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(realUser));

        UserTenantRepository tenantRepo = mock(UserTenantRepository.class);
        when(tenantRepo.findPrimaryOrganizationId(42L)).thenReturn(Optional.of(7L));
        when(tenantRepo.findPrimaryLibraryId(42L)).thenReturn(Optional.of(99L));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, uds, userRepository, tenantRepo);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        try {
            filter.doFilter(req, res, chain);

            assertEquals(7L, TenantContext.getOrganizationId());
            assertEquals(99L, TenantContext.getLibraryId());
        } finally {
            TenantContext.clear();
        }
    }
}
