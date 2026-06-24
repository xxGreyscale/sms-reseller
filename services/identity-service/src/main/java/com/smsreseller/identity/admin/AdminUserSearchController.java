package com.smsreseller.identity.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user search endpoint — ROLE_ADMIN gated by SecurityConfig (ADMN-02, T-05-05).
 *
 * <p>Route: GET /api/v1/admin/users?q={term}&page=0&size=20
 *
 * <p>No subject scoping — admin sees all users. The SecurityConfig ensures only
 * ROLE_ADMIN tokens can reach /api/v1/admin/**.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserSearchController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Page<UserSummaryDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.search(q, PageRequest.of(page, size)));
    }
}
