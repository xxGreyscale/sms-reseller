package com.smsreseller.wallet.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only endpoint for ledger inspection (ADMN-03).
 *
 * <p>Protected by SecurityConfig: {@code /api/v1/admin/**} requires {@code hasRole("ADMIN")}.
 * No subject-scoping — admin callers may inspect any user's credit transactions.
 *
 * <p>Trust boundary: verified at SecurityConfig. Admin can read any user's ledger;
 * ROLE_USER callers are rejected with 403 before reaching this controller.
 */
@RestController
@RequestMapping("/api/v1/admin/ledger")
@RequiredArgsConstructor
public class AdminLedgerController {

    private final AdminLedgerService adminLedgerService;

    /**
     * Returns a paginated ledger for the specified user, newest-first.
     *
     * @param userId the target user's UUID
     * @param page   zero-based page index (default 0)
     * @param size   page size (default 50)
     * @return paged list of {@link LedgerEntryDto}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Page<LedgerEntryDto>> getLedger(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<LedgerEntryDto> result = adminLedgerService.getLedger(
                userId, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }
}
