package com.smsreseller.identity.auth;

import com.smsreseller.identity.lockout.LoginAttemptService;
import com.smsreseller.identity.token.JwtIssuer;
import com.smsreseller.identity.token.RefreshTokenService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.web.dto.LoginRequest;
import com.smsreseller.identity.web.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles email + password login (IDEN-04).
 *
 * <p>Security properties:
 * <ul>
 *   <li>Lockout check happens BEFORE authentication (T-02-04, T-02-ENUM) — so an attacker
 *       cannot probe account existence via the lockout state.</li>
 *   <li>Both wrong-password AND unknown-email return the same generic 401 (T-02-ENUM):
 *       the {@link AuthenticationManager} throws {@link BadCredentialsException} for both,
 *       and we never inspect the cause to distinguish them.</li>
 *   <li>On success the lockout counter is reset (D-12).</li>
 *   <li>The access JWT carries the user's current {@code verification_status} (D-02).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String GENERIC_INVALID_CREDENTIALS_MSG = "Invalid credentials";

    private final AuthenticationManager authenticationManager;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;

    /**
     * Authenticates the user and issues an access+refresh token pair.
     *
     * @param req login request containing email, password, and deviceId
     * @return {@link TokenResponse} with access JWT + opaque refresh token
     * @throws ResponseStatusException 423 if account is locked; 401 on bad credentials
     */
    public TokenResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();

        // 1. Lockout check BEFORE any authentication attempt (T-02-04, T-02-ENUM)
        if (loginAttemptService.isLocked(email)) {
            log.warn("Login attempt blocked due to lockout for email={}", email);
            throw new ResponseStatusException(HttpStatus.valueOf(423), "Account temporarily locked");
        }

        // 2. Authenticate via Spring Security (DaoAuthenticationProvider + BCrypt)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (BadCredentialsException e) {
            // Increment lockout counter; return generic message (no enumeration, T-02-ENUM)
            loginAttemptService.increment(email);
            log.debug("Login failed for email={}", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_INVALID_CREDENTIALS_MSG);
        } catch (Exception e) {
            // Covers DisabledException, LockedException, etc. — still generic 401
            loginAttemptService.increment(email);
            log.debug("Login failed (non-credentials error) for email={}", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_INVALID_CREDENTIALS_MSG);
        }

        // 3. Success — reset lockout counter
        loginAttemptService.reset(email);

        // 4. Load user to get UUID + current verification status (D-02)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_INVALID_CREDENTIALS_MSG));

        // 5. Issue access JWT with current verification_status (D-02, D-06)
        String accessToken = jwtIssuer.issueAccessToken(user.getId(), user.getStatus());

        // 6. Issue opaque refresh token stored in Redis (D-06, D-08)
        String refreshToken = refreshTokenService.issue(user.getId(), req.deviceId());

        log.info("Login successful for userId={}", user.getId());
        return new TokenResponse(accessToken, refreshToken, user.getStatus().name());
    }
}
