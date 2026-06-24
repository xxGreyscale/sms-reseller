package com.smsreseller.contact.contact;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for contact CRUD (CONT-01/02/03).
 *
 * <p>IDOR guard (T-04-01): every handler derives userId exclusively from
 * {@code auth.getToken().getSubject()} — never from request body or path variables.
 * Cross-user access returns 404 (not 403) to avoid information leakage.
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    /**
     * CONT-01: Create a contact. userId from JWT subject (IDOR guard).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactDto create(
            JwtAuthenticationToken auth,
            @Valid @RequestBody CreateContactRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return ContactDto.from(contactService.create(userId, request.name(), request.phoneE164()));
    }

    /**
     * CONT-01: List contacts scoped to JWT subject.
     */
    @GetMapping
    public Page<ContactDto> list(
            JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return contactService.list(userId, PageRequest.of(page, size)).map(ContactDto::from);
    }

    /**
     * CONT-01: Get single contact by id, scoped to JWT subject.
     * Returns 404 if not found or belongs to another user (IDOR guard).
     */
    @GetMapping("/{id}")
    public ContactDto get(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return ContactDto.from(contactService.get(userId, id));
    }

    /**
     * CONT-02: Partial update of a contact's name and/or phone.
     */
    @PatchMapping("/{id}")
    public ContactDto update(
            JwtAuthenticationToken auth,
            @PathVariable UUID id,
            @RequestBody PatchContactRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return ContactDto.from(contactService.update(userId, id, request.name(), request.phoneE164()));
    }

    /**
     * CONT-03: Delete a contact (cascade removes from contact_group_members via DB FK).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        contactService.delete(userId, id);
    }

    // ── Inner request records ─────────────────────────────────────────────────

    public record CreateContactRequest(
            String name,
            @jakarta.validation.constraints.NotBlank(message = "phoneE164 is required")
            String phoneE164
    ) {}

    public record PatchContactRequest(String name, String phoneE164) {}
}
