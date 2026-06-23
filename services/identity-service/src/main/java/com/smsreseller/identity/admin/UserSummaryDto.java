package com.smsreseller.identity.admin;

import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User summary for admin operator view (ADMN-02, T-05-08 — no password fields).
 *
 * <p>Exposes exactly the columns shown in UI-SPEC User Search table:
 * id, fullName, email, phone, verificationStatus, createdAt.
 * The passwordHash field is intentionally excluded (T-05-08 mitigation).
 */
public record UserSummaryDto(
        UUID id,
        String fullName,
        String email,
        String phone,
        VerificationStatus verificationStatus,
        Instant createdAt
) {
    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
