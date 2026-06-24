package com.smsreseller.contact.contact;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for contact CRUD (CONT-01/02/03).
 *
 * <p>All methods take userId from the call site (which extracts it from JWT subject).
 * No method accepts userId from a DTO — userId is always caller-provided from the JWT.
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    @Transactional
    public Contact create(UUID userId, String name, String phoneE164) {
        Contact contact = Contact.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .phoneE164(phoneE164)
                .build();
        return contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public Page<Contact> list(UUID userId, Pageable pageable) {
        return contactRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Contact get(UUID userId, UUID contactId) {
        return contactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new ContactNotFoundException(
                        "Contact not found: " + contactId));
    }

    @Transactional
    public Contact update(UUID userId, UUID contactId, String name, String phoneE164) {
        Contact contact = get(userId, contactId);
        if (name != null) contact.setName(name);
        if (phoneE164 != null) contact.setPhoneE164(phoneE164);
        return contactRepository.save(contact);
    }

    @Transactional
    public void delete(UUID userId, UUID contactId) {
        Contact contact = get(userId, contactId);
        contactRepository.delete(contact);
    }

    /**
     * Returns distinct E.164 phones for the given contactIds, scoped to userId.
     * Suppressed numbers are excluded (MESG-09, T-06-03-02).
     *
     * <p>Used by InternalContactController to serve the messaging-service's
     * flat-contact campaign recipient expansion (D-12, MOBL-07).
     *
     * @param contactIds set of contact UUIDs to expand
     * @param userId     owner — contactIds belonging to other users are silently excluded (T-06-03-01)
     * @return unsuppressed phone numbers for the matching contacts
     */
    @Transactional(readOnly = true)
    public List<String> recipientsByContactIds(Set<UUID> contactIds, UUID userId) {
        if (contactIds == null || contactIds.isEmpty()) {
            return List.of();
        }
        return contactRepository.findPhonesByContactIdsAndUserId(contactIds, userId);
    }
}
