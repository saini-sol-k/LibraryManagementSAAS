package com.librarysaas.security.service;

import com.librarysaas.security.model.RefreshToken;
import com.librarysaas.security.repository.RefreshTokenRepository;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.jwt.JwtTokenProvider;
import com.librarysaas.security.jwt.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, UserRepository userRepository, JwtProperties jwtProperties, UserDetailsService userDetailsService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
        this.userDetailsService = userDetailsService;
    }

    // Create and persist a refresh token for userId, return token string (id:secret)
    public String createRefreshToken(Long userId) {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(passwordEncoder.encode(secret));
        rt.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSec()));
        rt.setCreatedAt(Instant.now());
        RefreshToken saved = refreshTokenRepository.save(rt);

        // Token format: id:secret
        return saved.getRefreshTokenId() + ":" + secret;
    }

    // Validate incoming refresh token; return userId if valid
    public Optional<Long> validateRefreshToken(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        Long id;
        try { id = Long.parseLong(parts[0]); } catch (NumberFormatException e) { return Optional.empty(); }
        String secret = parts[1];
        Optional<RefreshToken> opt = refreshTokenRepository.findById(id);
        if (opt.isEmpty()) return Optional.empty();
        RefreshToken rt = opt.get();
        if (rt.getRevokedAt() != null) return Optional.empty();
        if (rt.getExpiresAt() == null || rt.getExpiresAt().isBefore(Instant.now())) return Optional.empty();
        if (!passwordEncoder.matches(secret, rt.getTokenHash())) return Optional.empty();
        return Optional.of(rt.getUserId());
    }

    // Rotate: revoke old token and create a new one for same user
    public String rotateRefreshToken(String oldToken) {
        Optional<Long> userIdOpt = validateRefreshToken(oldToken);
        if (userIdOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        String[] parts = oldToken.split(":", 2);
        Long id = Long.parseLong(parts[0]);
        // revoke old
        refreshTokenRepository.findById(id).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepository.save(rt);
        });

        return createRefreshToken(userIdOpt.get());
    }

    public void revokeRefreshToken(String token) {
        if (token == null) return;
        String[] parts = token.split(":", 2);
        if (parts.length != 2) return;
        Long id;
        try { id = Long.parseLong(parts[0]); } catch (NumberFormatException e) { return; }
        refreshTokenRepository.findById(id).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepository.save(rt);
        });
    }
}
