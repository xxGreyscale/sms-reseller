package com.smsreseller.identity.auth;

import com.smsreseller.identity.password.PasswordResetService;
import com.smsreseller.identity.web.dto.ForgotPasswordRequest;
import com.smsreseller.identity.web.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password-reset endpoints (IDEN-07).
 *
 * <p>Both endpoints are {@code permitAll} in {@link com.smsreseller.identity.config.SecurityConfig}
 * (configured in plan 02-01).
 *
 * <p>Security notes:
 * <ul>
 *   <li>POST /auth/forgot always returns 200 — never reveals whether the email is registered
 *       (no enumeration, T-02-ENUM).</li>
 *   <li>POST /auth/reset returns 400 for invalid/expired/already-used tokens.</li>
 *   <li>The reset token is NEVER logged (T-02-LOG / V7).</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Initiates forgotten-password flow.
     *
     * <p>If the email is registered, a single-use time-limited reset link is sent to the
     * address. If the email is not registered, the same 200 response is returned (no
     * enumeration). The response body is intentionally minimal.
     *
     * @param request contains the email address
     * @return 200 OK always (no enumeration)
     */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgot(request.email());
        return ResponseEntity.ok().build();
    }

    /**
     * Completes the password reset.
     *
     * <p>Consumes the single-use token, updates the user's password hash, and revokes all
     * existing refresh tokens (D-09). Returns 400 if the token is invalid, expired, or
     * already used.
     *
     * @param request contains the reset token and new password
     * @return 200 OK on success; 400 for invalid/expired token
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
