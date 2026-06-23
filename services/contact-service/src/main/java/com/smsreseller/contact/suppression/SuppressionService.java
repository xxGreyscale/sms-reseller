package com.smsreseller.contact.suppression;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for per-user suppression list (CONT-08, D-08).
 */
@Service
@RequiredArgsConstructor
public class SuppressionService {

    private final SuppressionRepository suppressionRepository;

    /**
     * Suppress a phone number for a user. Idempotent: if the number is already
     * suppressed for this user, this is a no-op (DB unique constraint handles it).
     */
    @Transactional
    public SuppressedNumber suppress(UUID userId, String phoneE164) {
        // Check existence first for idempotency (avoids DataIntegrityViolationException)
        if (suppressionRepository.existsByUserIdAndPhoneE164(userId, phoneE164)) {
            return suppressionRepository.findByUserId(userId)
                    .stream()
                    .filter(s -> s.getPhoneE164().equals(phoneE164))
                    .findFirst()
                    .orElseThrow();
        }
        SuppressedNumber entry = SuppressedNumber.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .phoneE164(phoneE164)
                .build();
        return suppressionRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<SuppressedNumber> list(UUID userId) {
        return suppressionRepository.findByUserId(userId);
    }
}
