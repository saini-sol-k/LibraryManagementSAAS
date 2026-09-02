package com.librarysaas.security;

import com.librarysaas.security.model.RefreshToken;
import com.librarysaas.security.repository.RefreshTokenRepository;
import com.librarysaas.security.service.RefreshTokenService;
import com.librarysaas.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RefreshTokenServiceTest {

    @Test
    public void createValidateRotateRevoke() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtProperties props = new JwtProperties() {
            @Override public String getSecret() { return "change-me-dev-secret-do-not-use-in-prod"; }
            @Override public long getAccessTokenExpirationSec() { return 3600L; }
            @Override public long getRefreshTokenExpirationSec() { return 3600L; }
        };

        // We need simple behavior: save returns entity with id 1
        when(repo.save(any())).thenAnswer(inv -> {
            RefreshToken r = inv.getArgument(0);
            r.setRefreshTokenId(1L);
            return r;
        });

        // minimal stubs for other deps
        RefreshTokenService svc = new RefreshTokenService(repo, encoder, null, null, props, null);

        String token = svc.createRefreshToken(10L);
        assertNotNull(token);
        Optional<Long> v = svc.validateRefreshToken(token);
        // validate will fail because repo.findById not stubbed; simulate that
        when(repo.findById(1L)).thenReturn(Optional.of(new RefreshToken() {{ setRefreshTokenId(1L); setUserId(10L); setTokenHash(encoder.encode(token.split(":",2)[1])); setExpiresAt(java.time.Instant.now().plusSeconds(100)); }}));
        v = svc.validateRefreshToken(token);
        assertTrue(v.isPresent());
        assertEquals(10L, v.get());

        String rotated = svc.rotateRefreshToken(token);
        assertNotNull(rotated);
        // revoke old should have been called; simulate findById returns old token revoked
        when(repo.findById(1L)).thenReturn(Optional.of(new RefreshToken() {{ setRefreshTokenId(1L); setUserId(10L); setRevokedAt(java.time.Instant.now()); }}));
        Optional<Long> reused = svc.validateRefreshToken(token);
        assertTrue(reused.isEmpty());

        // revoke newly created token
        String[] parts = rotated.split(":",2);
        Long newId = Long.parseLong(parts[0]);
        svc.revokeRefreshToken(rotated);
        // nothing to assert beyond no exception
    }
}
