package com.opendesk.messaging.contact;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Abstraction for expanding a campaign's target group IDs into distinct E.164 recipient phones.
 *
 * <p>The implementation calls contact-service's internal query surface
 * ({@code ContactGroupRepository.findContactPhonesByGroupIdsAndUserId}) via REST and applies
 * suppression filtering at the contact-service boundary (D-14, MESG-09).
 *
 * <p>Tests inject a mock/fake implementation via {@code @MockBean}.
 *
 * <p>Why an interface? The contact-service internal REST endpoint is out-of-scope for this plan
 * (04-02 built the JPA query; we call it via an abstraction so tests are decoupled from HTTP).
 */
public interface ContactRecipientClient {

    /**
     * Returns distinct E.164 phone numbers for contacts in the given groups, scoped to the user.
     * Suppressed numbers are excluded at the contact-service boundary.
     *
     * @param groupIds set of group IDs (from Campaign.groupIds)
     * @param userId   campaign owner — recipients must belong to this user's groups
     * @return distinct unsuppressed phone numbers ready for dispatch
     */
    List<String> getRecipientsForGroups(Set<UUID> groupIds, UUID userId);

    /**
     * Returns distinct E.164 phone numbers for the given flat contactIds, scoped to the user.
     * Suppressed numbers are excluded at the contact-service boundary (D-12, MOBL-07, MESG-09).
     *
     * <p>ContactIds belonging to other users are silently excluded by the contact-service
     * (T-06-03-01 IDOR guard — userId is always from the JWT subject, not the request body).
     *
     * @param contactIds set of contact UUIDs (from Campaign.contactIds)
     * @param userId     campaign owner
     * @return distinct unsuppressed phone numbers ready for dispatch
     */
    List<String> getRecipientsByContactIds(Set<UUID> contactIds, UUID userId);
}
