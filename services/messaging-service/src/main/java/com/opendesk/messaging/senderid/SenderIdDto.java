package com.opendesk.messaging.senderid;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for sender-ID request API.
 */
public class SenderIdDto {

    /**
     * Request body for POST /api/v1/sender-ids/requests (SNDR-02).
     * senderName: max 11 chars, alphanumeric only (GSM spec).
     */
    public record SubmitRequest(
            @NotBlank
            @Size(max = 11, message = "senderName must be 11 characters or fewer")
            @Pattern(regexp = "^[A-Za-z0-9]+$", message = "senderName must be alphanumeric")
            String senderName
    ) {}

    /**
     * Request body for POST .../reject.
     */
    public record RejectRequest(String reason) {}

    /**
     * Response DTO for sender-ID request.
     */
    public record SenderIdResponse(
            UUID id,
            UUID userId,
            String senderName,
            String status,
            String rejectReason,
            Instant decidedAt,
            Instant createdAt
    ) {
        public static SenderIdResponse from(SenderIdRequest req) {
            return new SenderIdResponse(
                    req.getId(),
                    req.getUserId(),
                    req.getSenderName(),
                    req.getStatus().name(),
                    req.getRejectReason(),
                    req.getDecidedAt(),
                    req.getCreatedAt()
            );
        }
    }
}
