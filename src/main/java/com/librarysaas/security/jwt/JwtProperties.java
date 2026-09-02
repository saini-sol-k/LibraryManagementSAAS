package com.librarysaas.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProperties {
    @Value("${jwt.secret:change-me-dev-secret-do-not-use-in-prod}")
    private String secret;

    @Value("${jwt.access-token-expiration-sec:3600}")
    private long accessTokenExpirationSec;
    @Value("${jwt.refresh-token-expiration-sec:1209600}")
    // default 14 days
    private long refreshTokenExpirationSec;

    public String getSecret() { return secret; }
    public long getAccessTokenExpirationSec() { return accessTokenExpirationSec; }
    public long getRefreshTokenExpirationSec() { return refreshTokenExpirationSec; }
}
