package com.opendesk.identity.auth;

import com.opendesk.identity.token.JwtIssuer;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.user.VerificationStatus;
import com.opendesk.identity.verification.VerificationOrchestrator;
import com.opendesk.identity.web.dto.RegisterRequest;
import com.opendesk.identity.web.dto.RegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Handles user registration (IDEN-01 + IDEN-02).
 *
 * <p>Contract:
 * <ol>
 *   <li>Guard duplicate email / phone → 409 Conflict</li>
 *   <li>BCrypt-hash the password via {@link PasswordEncoder} (D-12)</li>
 *   <li>Persist user with status {@code PENDING_VERIFICATION}</li>
 *   <li>Issue a PENDING access JWT via {@link JwtIssuer} (D-01 "logged in but walled")</li>
 *   <li>Fire-and-forget async NIDA verification via {@link VerificationOrchestrator} (IDEN-02)</li>
 *   <li>Return immediately — the response MUST NOT block on the NIDA result</li>
 * </ol>
 *
 * <p>PII note: the NIN (National ID Number) is passed only to the orchestrator for async dispatch.
 * It is NEVER stored in the {@code users} table and MUST NEVER appear in any log line.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final VerificationOrchestrator verificationOrchestrator;

    /**
     * Registers a new user and returns immediately with a PENDING access JWT.
     *
     * @param request validated registration payload
     * @return response with userId, PENDING status, and access JWT
     * @throws ResponseStatusException 409 if email or phone already registered
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Duplicate guard (T-02-IDEN01)
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered");
        }

        // Hash password — plain-text never leaves this method
        String hashedPassword = passwordEncoder.encode(request.password());

        // Persist user as PENDING_VERIFICATION
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(hashedPassword)
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build();
        user = userRepository.save(user);

        // Issue PENDING access JWT (D-01 — client is "logged in but walled")
        String accessToken = jwtIssuer.issueAccessToken(user.getId(), VerificationStatus.PENDING_VERIFICATION);

        // Fire-and-forget async NIDA verification (IDEN-02 — must NOT block)
        // NIN is passed only here; never logged (T-02-PII)
        verificationOrchestrator.verifyAsync(user.getId(), request.nin());

        return new RegisterResponse(user.getId(), user.getStatus().name(), accessToken);
    }
}
