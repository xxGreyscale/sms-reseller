package com.opendesk.wallet.api;

import com.opendesk.wallet.balance.BalanceService;
import com.opendesk.wallet.transaction.CreditTransactionDto;
import com.opendesk.wallet.transaction.CreditTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Wallet read API — balance and transaction history.
 *
 * <p>ASVS V4 / IDOR: {@code userId} is ALWAYS sourced from the JWT subject
 * ({@code auth.getToken().getSubject()}). It is NEVER read from request body, path
 * parameter, or query string — doing so would allow one user to read another's data.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/wallet/balance} — WLET-01: derived available credits</li>
 *   <li>{@code GET /api/v1/wallet/transactions} — WLET-02: append-only ledger history,
 *       newest-first, paginated</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final BalanceService balanceService;
    private final CreditTransactionRepository creditTransactionRepository;

    /**
     * Returns the derived available SMS credit balance for the authenticated user (WLET-01).
     *
     * <p>Balance = SUM(granted - consumed - reserved) over non-expired lots. Expired lots
     * contribute zero (D-02, WLET-06). userId from JWT subject only — no IDOR possible.
     *
     * @param auth the JWT token of the authenticated user
     * @return balance response with availableCredits field
     */
    @GetMapping("/balance")
    public BalanceResponse getBalance(@AuthenticationPrincipal Jwt auth) {
        UUID userId = UUID.fromString(auth.getSubject());
        int available = balanceService.getBalance(userId);
        return new BalanceResponse(available);
    }

    /**
     * Returns the paginated, newest-first transaction history for the authenticated user (WLET-02).
     *
     * <p>Only returns rows owned by the JWT subject — cross-user isolation is enforced at the
     * repository query level (WHERE user_id = :userId). userId from JWT subject only — no IDOR.
     *
     * @param auth     the JWT token of the authenticated user
     * @param pageable pagination parameters (default: page=0, size=20, sort by createdAt DESC)
     * @return page of {@link CreditTransactionDto} newest-first
     */
    @GetMapping("/transactions")
    public Page<CreditTransactionDto> getTransactionHistory(
            @AuthenticationPrincipal Jwt auth,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(auth.getSubject());
        return creditTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(CreditTransactionDto::from);
    }
}
