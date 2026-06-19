package com.opendesk.identity.auth;

import com.opendesk.identity.web.dto.RegisterRequest;
import com.opendesk.identity.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user registration.
 *
 * <p>POST /auth/register is permit-all in {@code SecurityConfig} — no JWT required to register.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * Register a new user.
     *
     * <p>Returns 200 with a PENDING access JWT immediately (IDEN-01/02).
     * Async NIDA verification runs in the background.
     *
     * @param request validated registration payload (@Valid triggers Bean Validation)
     * @return RegisterResponse with userId, status=PENDING_VERIFICATION, accessToken
     */
    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
