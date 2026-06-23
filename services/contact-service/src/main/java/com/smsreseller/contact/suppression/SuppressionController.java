package com.smsreseller.contact.suppression;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for per-user suppression list (CONT-08, D-08).
 *
 * <p>IDOR guard (T-04-01): userId derived exclusively from
 * {@code auth.getToken().getSubject()}.
 */
@RestController
@RequestMapping("/api/v1/suppression")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionService suppressionService;

    /**
     * Suppress a phone number for the authenticated user. Idempotent.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuppressionDto suppress(
            JwtAuthenticationToken auth,
            @Valid @RequestBody SuppressRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return SuppressionDto.from(suppressionService.suppress(userId, request.phoneE164()));
    }

    /**
     * List suppressed numbers for the authenticated user.
     */
    @GetMapping
    public List<SuppressionDto> list(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return suppressionService.list(userId)
                .stream()
                .map(SuppressionDto::from)
                .toList();
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    public record SuppressRequest(
            @NotBlank(message = "phoneE164 is required") String phoneE164
    ) {}

    public record SuppressionDto(UUID id, UUID userId, String phoneE164, Instant createdAt) {
        static SuppressionDto from(SuppressedNumber s) {
            return new SuppressionDto(s.getId(), s.getUserId(), s.getPhoneE164(), s.getCreatedAt());
        }
    }
}
