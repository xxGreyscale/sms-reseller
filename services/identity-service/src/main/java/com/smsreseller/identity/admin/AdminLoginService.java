package com.smsreseller.identity.admin;

import com.smsreseller.identity.token.JwtIssuer;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles admin credential validation and ROLE_ADMIN JWT issuance (ADMN-01, D-02).
 *
 * <p>Security properties:
 * <ul>
 *   <li>Only accounts with role=ADMIN can obtain admin tokens (T-05-05 mitigation).</li>
 *   <li>Wrong password AND non-admin email both return the same generic 401 — no enumeration.</li>
 *   <li>Password verified via DelegatingPasswordEncoder (BCrypt, same as user login path).</li>
 *   <li>No lockout at MVP — admin is a single internal seeded account behind Traefik (T-05-06 accepted).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginService {

    private static final String INVALID_MSG = "Invalid credentials";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    /**
     * Validates admin credentials and issues a ROLE_ADMIN JWT.
     *
     * @param email    admin email
     * @param password plaintext password to verify against stored BCrypt hash
     * @return signed ROLE_ADMIN JWT (60-minute TTL)
     * @throws ResponseStatusException 401 on bad credentials or non-ADMIN role
     */
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> {
                    log.debug("Admin login failed — email not found: {}", email);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_MSG);
                });

        // Reject non-ADMIN accounts attempting to use the admin login endpoint (T-05-05)
        if (user.getRole() != UserRole.ADMIN) {
            log.debug("Admin login rejected — account does not have ADMIN role: userId={}", user.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_MSG);
        }

        // Verify password (DelegatingPasswordEncoder handles {bcrypt} prefix)
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.debug("Admin login failed — wrong password: userId={}", user.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_MSG);
        }

        log.info("Admin login successful: userId={}", user.getId());
        return jwtIssuer.issueAdminToken(user.getId());
    }
}
