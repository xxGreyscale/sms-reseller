package com.opendesk.contact.contact;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal service-to-service endpoints for contact-service.
 *
 * <p>These endpoints are called only by messaging-service (no external exposure).
 * The path prefix {@code /api/v1/internal/} is permit-all in SecurityConfig so
 * messaging-service does not need to supply a JWT — Traefik network policy restricts
 * access at the infrastructure layer.
 *
 * <p>See SecurityConfig: {@code /api/v1/internal/**} is in the permitAll list.
 */
@RestController
@RequestMapping("/api/v1/internal/contacts")
@RequiredArgsConstructor
public class InternalContactController {

    private final ContactService contactService;

    /**
     * Expand a set of flat contactIds to distinct, unsuppressed E.164 phone numbers
     * scoped to the specified userId (D-12, MOBL-07).
     *
     * <p>Security:
     * <ul>
     *   <li>userId is supplied by the calling service (messaging-service passes the JWT subject
     *       it already validated — defense-in-depth userId scope).</li>
     *   <li>ContactIds belonging to other users are silently excluded (T-06-03-01).</li>
     *   <li>Suppressed numbers for this userId are excluded (T-06-03-02, MESG-09).</li>
     * </ul>
     *
     * @param contactIds comma-separated contact UUIDs
     * @param userId     the campaign owner's UUID (from messaging-service JWT subject)
     * @return list of distinct unsuppressed E.164 phones
     */
    @GetMapping("/recipients-by-ids")
    public List<String> recipientsByIds(
            @RequestParam String contactIds,
            @RequestParam UUID userId) {

        Set<UUID> ids = Arrays.stream(contactIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toSet());

        return contactService.recipientsByContactIds(ids, userId);
    }
}
