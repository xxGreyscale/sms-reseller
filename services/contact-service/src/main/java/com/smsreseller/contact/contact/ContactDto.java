package com.smsreseller.contact.contact;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Request/response DTO for contact CRUD endpoints.
 *
 * <p>phone is @NotBlank on create; on PATCH it may be null (partial update).
 * userId is NEVER accepted from the client — always derived from JWT subject.
 */
public record ContactDto(
        UUID id,
        String name,
        @NotBlank(message = "phoneE164 is required") String phoneE164,
        Instant createdAt,
        Instant updatedAt
) {
    public static ContactDto from(Contact c) {
        return new ContactDto(c.getId(), c.getName(), c.getPhoneE164(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
