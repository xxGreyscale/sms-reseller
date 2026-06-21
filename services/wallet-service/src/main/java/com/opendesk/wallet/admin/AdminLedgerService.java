package com.opendesk.wallet.admin;

import com.opendesk.wallet.transaction.CreditTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service backing the admin ledger inspection endpoint (ADMN-03).
 *
 * <p>Reads any user's credit transactions without subject-scoping — callers must
 * be authenticated with ROLE_ADMIN (enforced by SecurityConfig, not here).
 */
@Service
@RequiredArgsConstructor
public class AdminLedgerService {

    private final CreditTransactionRepository creditTransactionRepository;

    /**
     * Returns a page of ledger entries for the specified user, newest-first.
     *
     * @param userId   the user whose ledger is being inspected
     * @param pageable pagination parameters (page, size, sort)
     * @return paged {@link LedgerEntryDto} results
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryDto> getLedger(UUID userId, Pageable pageable) {
        return creditTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(LedgerEntryDto::from);
    }
}
