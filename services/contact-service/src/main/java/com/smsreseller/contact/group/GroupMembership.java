package com.smsreseller.contact.group;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.util.UUID;

/**
 * Explicit join entity for contact_group_members (group_id, contact_id PK).
 *
 * <p>Using an explicit entity instead of @ManyToMany improves testability and
 * gives direct query access to membership rows.
 * ON DELETE CASCADE is enforced both via SQL migration (Flyway) and via
 * {@link OnDelete} for Hibernate DDL (test profile uses ddl-auto=create-drop).
 */
@Entity
@Table(name = "contact_group_members")
@IdClass(GroupMembership.MembershipId.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupMembership {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "contact_id")
    private UUID contactId;

    /**
     * FK to contact_groups with ON DELETE CASCADE.
     * The @ManyToOne is declared as insertable=false, updatable=false so that
     * groupId (the @Id) stays the single source of truth for the column value.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_cgm_group"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ContactGroup group;

    /**
     * FK to contacts with ON DELETE CASCADE.
     * insertable=false/updatable=false keeps contactId as the authoritative column.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_cgm_contact"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private com.smsreseller.contact.contact.Contact contact;

    // ── Composite PK ─────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @EqualsAndHashCode
    public static class MembershipId implements Serializable {
        private UUID groupId;
        private UUID contactId;
    }
}
