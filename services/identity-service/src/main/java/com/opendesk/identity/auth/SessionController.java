package com.opendesk.identity.auth;

import com.opendesk.identity.token.InvalidRefreshTokenException;
import com.opendesk.identity.token.JwtIssuer;
import com.opendesk.identity.token.RefreshTokenService;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.web.dto.LoginRequest;
import com.opendesk.identity.web.dto.RefreshRequest;
import com.opendesk.identity.web.dto.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for session lifecycle: login, refresh, logout.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /auth/login} — email+password → access+refresh (IDEN-04, permitted to all)</li>
 *   <li>{@code POST /auth/refresh} — rotate refresh token → new access+refresh (IDEN-05, permitted to all)</li>
 *   <li>{@code POST /auth/logout} — revoke current device refresh token (IDEN-06, authenticated)</li>
 * </ul>
 *
 * <p>Security:
 * <ul>
 *   <li>/auth/login and /auth/refresh are in the {@code permitAll} list in SecurityConfig</li>
 *   <li>/auth/logout requires a valid JWT (anyRequest().authenticated())</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SessionController {

    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final JwtIssuer jwtIssuer;
    private final UserRepository userRepository;

    /**
     * Login endpoint (IDEN-04).
     *
     * <p>Validates credentials, enforces lockout, and returns an access+refresh token pair.
     * Lockout and credential errors return identical 401 or 423 without revealing
     * account existence (T-02-ENUM).
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        TokenResponse response = loginService.login(req);
        return ResponseEntity.ok(response);
    }

    /**
     * Token refresh endpoint (IDEN-05, D-08).
     *
     * <p>Rotates the refresh token: the presented token is invalidated and a new pair
     * (access + refresh) is returned. Reuse of an already-rotated token triggers revokeAll (Pitfall 4).
     *
     * <p>Re-reads user status from the database (Pitfall 3) so a freshly-VERIFIED user
     * immediately receives a VERIFIED claim in their new access token without needing to logout
     * and login again.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            String newRawToken = refreshTokenService.rotate(req.refreshToken());

            // Parse userId from the new raw token to re-load user status (Pitfall 3)
            String[] parts = newRawToken.split("\\|", 3);
            if (parts.length < 2) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }
            UUID userId;
            try {
                userId = UUID.fromString(parts[0]);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }

            // Re-read user status to pick up VERIFIED state (Pitfall 3)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

            String newAccessToken = jwtIssuer.issueAccessToken(user.getId(), user.getStatus());
            return ResponseEntity.ok(new TokenResponse(newAccessToken, newRawToken, user.getStatus().name()));

        } catch (InvalidRefreshTokenException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
    }

    /**
     * Logout endpoint (IDEN-06, D-07).
     *
     * <p>Revokes only the current device's refresh token, leaving other device sessions intact.
     * Requires a valid access JWT (authenticated endpoint). The deviceId is passed in the request
     * body to identify which Redis key to delete.
     *
     * @param jwt   authenticated user's JWT (injected by Spring Security)
     * @param body  request body containing {@code deviceId}
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, @NotBlank String> body) {

        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceId is required");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        refreshTokenService.revokeCurrent(userId, deviceId);
        log.info("Logout: revoked session for userId={} deviceId={}", userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
