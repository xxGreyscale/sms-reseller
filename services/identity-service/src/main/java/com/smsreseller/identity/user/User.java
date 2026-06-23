package com.smsreseller.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;


/**
 * User aggregate root — stores credentials and NIDA verification status.
 *
 * <p>Security notes:
 * <ul>
 *   <li>{@link #passwordHash} is stored as a BCrypt hash (via DelegatingPasswordEncoder).
 *       It MUST NEVER appear in any DTO, API response, or log line.</li>
 *   <li>{@link #status} is the durable source of truth; the JWT carries it as a 15-min-max
 *       cache claim (D-02). On verification the user must re-login or refresh to get VERIFIED
 *       in their token (Pitfall 3).</li>
 * </ul>
 *
 * <p>UTF-8: all varchar columns use the database default encoding (PostgreSQL UTF-8 default).
 * Tanzanian names (Swahili, UTF-8) are stored correctly without any extra configuration.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Phone number — nullable for operator admin accounts (seeded via Flyway, no phone required).
     * Required for all self-registered USER accounts (enforced at registration layer).
     */
    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "full_name")
    private String fullName;

    /**
     * BCrypt password hash — NEVER exposed in DTOs or serialized to any external representation.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Account role — USER for self-registered accounts, ADMIN for seeded operator accounts (D-02).
     * Defaults to USER so existing registration flow does not need changes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING_VERIFICATION;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
