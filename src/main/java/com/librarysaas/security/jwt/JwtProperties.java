package com.librarysaas.security.jwt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

@Component
public class JwtProperties implements EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);

    /**
     * The fallback baked into the repository so a developer can clone and run. It is public
     * in source control, so a deployment that relies on it can have its tokens forged by
     * anyone. Startup fails outside the development profiles unless JWT_SECRET is supplied.
     */
    static final String DEVELOPMENT_FALLBACK_SECRET = "change-me-dev-secret-do-not-use-in-prod";

    /** HS256 signing keys must be at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "test", "integration-test");

    /** Set by Spring; null when the class is instantiated directly, e.g. in a unit test. */
    private Environment environment;

    @Value("${jwt.secret:" + DEVELOPMENT_FALLBACK_SECRET + "}")
    private String secret;

    @Value("${jwt.access-token-expiration-sec:3600}")
    private long accessTokenExpirationSec;
    @Value("${jwt.refresh-token-expiration-sec:1209600}")
    // default 14 days
    private long refreshTokenExpirationSec;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * Refuses to start a deployment that would sign tokens with the public development
     * secret, and rejects any secret too short for HS256. The secret value itself is never
     * logged or included in the failure message.
     */
    @PostConstruct
    void validateSecret() {
        String configuredSecret = getSecret();

        if (DEVELOPMENT_FALLBACK_SECRET.equals(configuredSecret)) {
            if (!isDevelopmentProfileActive()) {
                throw new IllegalStateException(
                        "jwt.secret is still the built-in development fallback. Set the JWT_SECRET "
                                + "environment variable to a unique value of at least " + MIN_SECRET_BYTES
                                + " bytes before starting outside the " + DEVELOPMENT_PROFILES + " profiles.");
            }
            log.warn("Using the built-in development JWT secret. This value is public in source "
                    + "control - never start a deployment without JWT_SECRET set.");
        }

        int secretBytes = configuredSecret == null
                ? 0
                : configuredSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_BYTES
                    + " bytes for HS256 but was " + secretBytes + " bytes.");
        }

        if (getAccessTokenExpirationSec() <= 0 || getRefreshTokenExpirationSec() <= 0) {
            throw new IllegalStateException("JWT token expirations must be positive.");
        }
    }

    private boolean isDevelopmentProfileActive() {
        if (environment == null) {
            // Not running inside a Spring context; the profile guard cannot apply.
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(DEVELOPMENT_PROFILES::contains);
    }

    public String getSecret() { return secret; }

    /**
     * Charset-explicit so the signing key cannot vary with the platform default encoding.
     * Derived from {@link #getSecret()} so overrides are honoured.
     */
    public byte[] getSecretBytes() { return getSecret().getBytes(StandardCharsets.UTF_8); }

    public long getAccessTokenExpirationSec() { return accessTokenExpirationSec; }
    public long getRefreshTokenExpirationSec() { return refreshTokenExpirationSec; }
}
