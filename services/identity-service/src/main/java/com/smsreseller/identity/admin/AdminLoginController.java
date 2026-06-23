package com.smsreseller.identity.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin login endpoint — issues ROLE_ADMIN JWT for operator access to admin-web (ADMN-01).
 *
 * <p>Route: POST /api/v1/auth/admin/login (permitAll in SecurityConfig — no JWT required to call this).
 *
 * <p>Response: {@code {"accessToken": "<jwt>"}} with 60-minute TTL.
 * No refresh token — admin sessions are short-lived (RESEARCH.md Pitfall 6).
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminLoginService adminLoginService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody AdminLoginRequest request) {
        String token = adminLoginService.login(request.email(), request.password());
        return ResponseEntity.ok(Map.of("accessToken", token));
    }
}
