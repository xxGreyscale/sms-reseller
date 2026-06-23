---
phase: 06-flutter-mobile-app
plan: "03"
subsystem: contact-service + messaging-service
tags: [contactIds, flat-contacts, campaign-targeting, recipients-by-ids, suppression, idor, tdd, mobl-07, d-12, mesg-09]
dependency_graph:
  requires: [04-05, 04-02]
  provides: [recipients-by-ids-endpoint, contactIds-campaign-path, flat-contact-dispatch]
  affects: [06-10, 06-11]
tech_stack:
  added: []
  patterns:
    - "InternalContactController: permitAll for /api/v1/internal/** — service-to-service, no JWT required from messaging-service"
    - "ContactRepository.findPhonesByContactIdsAndUserId: native SQL with suppression exclusion sub-query (MESG-09)"
    - "CampaignService.create guard: IllegalStateException when both groupIds and contactIds empty (T-06-03-03)"
    - "CampaignService.executeSend branch: contactIds non-empty → getRecipientsByContactIds; else existing group path (D-12)"
    - "Campaign.contactIds: @ElementCollection in campaign_contact_ids table (V7 migration)"
key_files:
  created:
    - services/contact-service/src/main/java/com/smsreseller/contact/contact/InternalContactController.java
    - services/messaging-service/src/main/resources/db/migration/V7__campaign_contact_ids.sql
    - services/contact-service/src/test/java/com/smsreseller/contact/contact/RecipientsByContactIdsIT.java
    - services/messaging-service/src/test/java/com/smsreseller/messaging/campaign/FlatContactCampaignIT.java
  modified:
    - services/contact-service/src/main/java/com/smsreseller/contact/contact/ContactRepository.java (added findPhonesByContactIdsAndUserId)
    - services/contact-service/src/main/java/com/smsreseller/contact/contact/ContactService.java (added recipientsByContactIds)
    - services/contact-service/src/main/java/com/smsreseller/contact/config/SecurityConfig.java (permit /api/v1/internal/**)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CreateCampaignRequest.java (relax groupIds, add contactIds)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/Campaign.java (add contactIds @ElementCollection)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignService.java (create guard + executeSend branch)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CampaignController.java (catch IllegalStateException on create)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/contact/ContactRecipientClient.java (add getRecipientsByContactIds)
    - services/messaging-service/src/main/java/com/smsreseller/messaging/contact/RestContactRecipientClient.java (implement getRecipientsByContactIds)
decisions:
  - "Flyway V7 (not V6) for campaign_contact_ids: V6__add_operator_to_outbound_messages.sql already existed; plan named V6 but V7 is the correct next version"
  - "InternalContactController /api/v1/internal/** is permitAll: mirrors the established internal-call posture — no JWT passed between services at this layer; Traefik network policy restricts external exposure"
  - "CampaignController.create now catches IllegalStateException and returns 400: no global @ControllerAdvice exists; consistent with cancel() returning 409 for illegal state transitions"
  - "executeSend branches on contactIds non-empty first, then falls through to groupIds path: this preserves existing group campaigns without any change to that code path"
metrics:
  duration: ~25 minutes
  completed: 2026-06-22
  tasks: 2
  files: 13
---

# Phase 06 Plan 03: Campaign contactIds Targeting + recipients-by-ids Summary

**One-liner:** New contactIds[] campaign targeting field (relaxed CreateCampaignRequest + @ElementCollection + V7 migration + executeSend branch) paired with a userId-scoped, suppression-filtered GET /api/v1/internal/contacts/recipients-by-ids endpoint in contact-service, closing backend gap D-12 for the Flutter flat-contact composer (MOBL-07).

## What Was Built

### contact-service: recipients-by-ids endpoint

**GET /api/v1/internal/contacts/recipients-by-ids?contactIds=A,B&userId=U**

Returns distinct unsuppressed E.164 phones for the requested contactIds scoped to the specified userId.

| Layer | What | Detail |
|-------|------|--------|
| `ContactRepository.findPhonesByContactIdsAndUserId` | JPQL native query | `WHERE id IN (:contactIds) AND user_id = :userId AND phone_e164 NOT IN (suppressed subquery)` |
| `ContactService.recipientsByContactIds` | Service method | Delegates to repo; handles empty input; `@Transactional(readOnly=true)` |
| `InternalContactController.recipientsByIds` | REST endpoint | `GET /api/v1/internal/contacts/recipients-by-ids` — parses comma-separated contactIds param |
| `SecurityConfig` | Permission | `/api/v1/internal/**` added to permitAll — no JWT needed for service-to-service calls |

**IDOR guard:** contactIds from other users are silently excluded by the `user_id = :userId` clause (T-06-03-01).
**Suppression:** excluded by the NOT IN subquery against `suppressed_numbers` (T-06-03-02, MESG-09).

### messaging-service: contactIds campaign path

**CreateCampaignRequest** — relaxed from `@NotNull @Size(min=1) groupIds` to nullable groupIds plus new `contactIds` field. Service-layer guard enforces at least one targeting source.

**Campaign entity** — added `contactIds @ElementCollection` stored in `campaign_contact_ids` table (V7 migration).

**CampaignService.create** — guard: `if both groupIds and contactIds empty → IllegalStateException` (T-06-03-03).

**CampaignService.executeSend** branch:
```java
if (campaign.getContactIds() != null && !campaign.getContactIds().isEmpty()) {
    recipients = contactRecipientClient.getRecipientsByContactIds(campaign.getContactIds(), userId);
} else {
    recipients = contactRecipientClient.getRecipientsForGroups(campaign.getGroupIds(), userId);
}
```
Remainder of dispatch pipeline (reserve → zip → QUEUED → publish) is unchanged.

**ContactRecipientClient** — new `getRecipientsByContactIds(Set<UUID> contactIds, UUID userId)` method.

**RestContactRecipientClient** — implementation calls `GET /api/v1/internal/contacts/recipients-by-ids?contactIds=...&userId=...`.

### API Contract for 06-10/06-11 Flutter composer

```
POST /api/v1/campaigns
{
  "name": "Member Blast",
  "body": "Hello members!",
  "senderId": "SMSRESELLER",
  "contactIds": ["<uuid1>", "<uuid2>"]   // flat contacts — no groupIds needed
}
```

Response: `201 Created` with campaign in `DRAFT` status. Then `POST /api/v1/campaigns/{id}/send` to dispatch.

## TDD Gate Compliance

| Gate | Task | Status | Commit |
|------|------|--------|--------|
| RED (contact-service) | Task 1 | PASSED | `970d949` — RecipientsByContactIdsIT failing (endpoint missing) |
| GREEN (contact-service) | Task 1 | PASSED | `e2aedc2` — endpoint implemented, all 3 tests GREEN |
| RED (messaging-service) | Task 2 | PASSED | `694c1a2` — FlatContactCampaignIT failing (contactIds not on interface) |
| GREEN (messaging-service) | Task 2 | PASSED | `0c0fa06` — full contactIds path implemented, all 3 tests GREEN |

## Deviations from Plan

**1. [Rule 1 - Bug] Flyway migration version correction**
- **Found during:** Task 2
- **Issue:** Plan specified `V6__campaign_contact_ids.sql` but `V6__add_operator_to_outbound_messages.sql` already exists; using V6 would cause Flyway checksum failure
- **Fix:** Used `V7__campaign_contact_ids.sql` — next available version
- **Files modified:** `services/messaging-service/src/main/resources/db/migration/V7__campaign_contact_ids.sql`

**2. [Rule 2 - Missing handler] CampaignController.create IllegalStateException handling**
- **Found during:** Task 2
- **Issue:** No global @ControllerAdvice; the guard exception in CampaignService.create would propagate as 500 rather than a client error
- **Fix:** Added try/catch in CampaignController.create returning 400 with error body
- **Files modified:** `CampaignController.java`

## Threat Model Coverage

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-06-03-01 (IDOR — foreign contactIds) | MITIGATED | `WHERE user_id = :userId` in query; RecipientsByContactIdsIT test 2 asserts cross-user exclusion |
| T-06-03-02 (bypass suppression via flat contactIds) | MITIGATED | NOT IN subquery in findPhonesByContactIdsAndUserId; RecipientsByContactIdsIT test 3 asserts suppression exclusion |
| T-06-03-03 (empty-target campaign queued) | MITIGATED | create() guard rejects when both empty → 400; FlatContactCampaignIT test 2 asserts rejection |

## Known Stubs

None. Both contact-service endpoint and messaging-service path are fully wired end-to-end.

Note: the existing group-path `RestContactRecipientClient.getRecipientsForGroups` still calls contact-service `GET /api/v1/internal/contacts/recipients` which remains unimplemented in contact-service (tracked in 04-05-SUMMARY as pre-launch task). This is pre-existing and out-of-scope for this plan.

## Self-Check: PASSED

- `services/contact-service/src/main/java/com/smsreseller/contact/contact/InternalContactController.java`: EXISTS
- `services/messaging-service/src/main/resources/db/migration/V7__campaign_contact_ids.sql`: EXISTS
- `services/messaging-service/src/main/java/com/smsreseller/messaging/campaign/CreateCampaignRequest.java`: contains `contactIds`
- Commits 970d949, e2aedc2, 694c1a2, 0c0fa06: all present in git log
- Full test suite `./gradlew :services:contact-service:test :services:messaging-service:test`: BUILD SUCCESSFUL
