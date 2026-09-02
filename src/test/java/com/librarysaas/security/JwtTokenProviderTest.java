package com.librarysaas.security;

import com.librarysaas.security.jwt.JwtProperties;
import com.librarysaas.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwtTokenProviderTest {

    @Test
    public void createAndValidateToken() {
        JwtProperties props = new JwtProperties() {
            @Override
            public String getSecret() { return "change-me-dev-secret-do-not-use-in-prod"; }

            @Override
            public long getAccessTokenExpirationSec() { return 3600L; }
        };

        JwtTokenProvider provider = new JwtTokenProvider(props);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = provider.createToken(auth);
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("testuser", provider.getUsername(token));
    }
}
