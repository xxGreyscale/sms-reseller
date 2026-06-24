package com.smsreseller.contact.contact;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for a contact owned by a single user.
 *
 * <p>IDOR: userId is set once at creation (updatable=false). All repo queries
 * filter by userId from JWT subject — never from the request path or body.
 *
 * <p>Unique constraint uq_contact_user_phone ensures a user cannot store the
 * same E.164 number twice (deduplication for CSV import in 04-03).
 */
@Entity
@Table(
        name = "contacts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_contact_user_phone",
                columnNames = {"user_id", "phone_e164"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Contact {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name")
    private String name;

    @Column(name = "phone_e164", nullable = false)
    private String phoneE164;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
