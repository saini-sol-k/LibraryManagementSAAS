package com.librarysaas.security.auth;

import com.librarysaas.common.response.ApiResponse;
import com.librarysaas.common.exception.UnauthorizedException;
import com.librarysaas.security.jwt.JwtProperties;
import com.librarysaas.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.userdetails.UserDetailsService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final com.librarysaas.security.service.RefreshTokenService refreshTokenService;
    private final com.librarysaas.security.repository.UserRepository userRepository;
    private final UserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, JwtProperties jwtProperties, com.librarysaas.security.service.RefreshTokenService refreshTokenService, com.librarysaas.security.repository.UserRepository userRepository, UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.identifier(), req.password())
            );

            String token = tokenProvider.createToken(auth);

            // Determine user id from repository using authenticated username
            String username = auth.getName();
            Long resolvedUserId = null;
            try {
                var userEntity = userRepository.findByUsernameOrEmail(username);
                if (userEntity.isPresent()) resolvedUserId = userEntity.get().getUserId();
            } catch (Exception ex) {
                // ignore
            }

            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", token);
            data.put("tokenType", "Bearer");
            data.put("expiresInSec", jwtProperties.getAccessTokenExpirationSec());

            if (resolvedUserId != null) {
                String refreshToken = refreshTokenService.createRefreshToken(resolvedUserId);
                data.put("refreshToken", refreshToken);
                data.put("refreshExpiresInSec", jwtProperties.getRefreshTokenExpirationSec());
            }

            return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", data));
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid username or password", "INVALID_CREDENTIALS");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            String oldRefresh = req.refreshToken();
            var userIdOpt = refreshTokenService.validateRefreshToken(oldRefresh);
            if (userIdOpt.isEmpty()) {
                throw new UnauthorizedException("Invalid or expired refresh token", "INVALID_REFRESH_TOKEN");
            }

            Long userId = userIdOpt.get();
            // rotate refresh token
            String newRefresh = refreshTokenService.rotateRefreshToken(oldRefresh);

            // create new access token for user
            var userEntity = userRepository.findById(userId);
            if (userEntity.isEmpty()) {
                throw new UnauthorizedException("Invalid refresh token", "INVALID_REFRESH_TOKEN");
            }
            String username = userEntity.get().getUsername();
            var userDetails = userDetailsService.loadUserByUsername(username);
            var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            String newAccess = tokenProvider.createToken(auth);

            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", newAccess);
            data.put("tokenType", "Bearer");
            data.put("expiresInSec", jwtProperties.getAccessTokenExpirationSec());
            data.put("refreshToken", newRefresh);
            data.put("refreshExpiresInSec", jwtProperties.getRefreshTokenExpirationSec());

            return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed", data));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired refresh token", "INVALID_REFRESH_TOKEN");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest req) {
        String rt = req.refreshToken();
        try {
            refreshTokenService.revokeRefreshToken(rt);
        } catch (Exception e) {
            // ignore
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out", null));
    }

    /**
     * Spring MVC logs the deserialized request body at DEBUG, and a record's generated
     * toString() would put the plaintext credential in the log file. Both records redact
     * their secret component so the credential cannot leak at any log level or profile.
     */
    public static record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[identifier=" + identifier + ", password=***]";
        }
    }

    public static record RefreshRequest(@NotBlank String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequest[refreshToken=***]";
        }
    }
}
