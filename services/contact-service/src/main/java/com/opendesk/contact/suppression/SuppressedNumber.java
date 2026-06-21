package com.opendesk.contact.suppression;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a suppressed phone number per user (CONT-08, D-08).
 *
 * <p>Suppression is scoped per-user globally: the same phone suppressed by
 * user A does NOT suppress it for user B.
 */
@Entity
@Table(name = "suppressed_numbers")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SuppressedNumber {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "phone_e164", nullable = false, updatable = false)
    private String phoneE164;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
