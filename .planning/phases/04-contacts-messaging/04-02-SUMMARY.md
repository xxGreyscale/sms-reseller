---
phase: 04-contacts-messaging
plan: "02"
subsystem: contact-service
tags: [contact, group, suppression, idor, crud, flyway, jpa]
dependency_graph:
  requires: [04-01]
  provides: [CONT-01, CONT-02, CONT-03, CONT-04, CONT-08]
  affects: [04-03, 04-05]
tech_stack:
  added: []
  patterns:
    - "IDOR guard: userId derived exclusively from auth.getToken().getSubject() on every handler"
    - "@OnDelete(CASCADE) Hibernate annotation on GroupMembership FKs (ensures cascade under ddl-auto=create-drop)"
    - "Idempotent suppression: check-exists before insert to avoid DataIntegrityViolationException"
    - "Explicit join entity (GroupMembership @IdClass) instead of @ManyToMany for testability"
key_files:
  created:
    - services/contact-service/src/main/resources/db/migration/V1__create_contacts.sql
    - services/contact-service/src/main/resources/db/migration/V2__create_contact_groups.sql
    - services/contact-service/src/main/resources/db/migration/V3__create_suppressed_numbers.sql
    - services/contact-service/src/main/java/com/opendesk/contact/config/SecurityConfig.java
    - services/contact-service/src/main/java/com/opendesk/contact/contact/Contact.java
    - services/contact-service/src/main/java/com/opendesk/contact/contact/ContactRepository.java
    - services/contact-service/src/main/java/com/opendesk/contact/contact/ContactService.java
    - services/contact-service/src/main/java/com/opendesk/contact/contact/ContactController.java
    - services/contact-service/src/main/java/com/opendesk/contact/contact/ContactDto.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/ContactGroup.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/GroupMembership.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/ContactGroupRepository.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/GroupMembershipRepository.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/GroupService.java
    - services/contact-service/src/main/java/com/opendesk/contact/group/GroupController.java
    - services/contact-service/src/main/java/com/opendesk/contact/suppression/SuppressedNumber.java
    - services/contact-service/src/main/java/com/opendesk/contact/suppression/SuppressionRepository.java
    - services/contact-service/src/main/java/com/opendesk/contact/suppression/SuppressionService.java
    - services/contact-service/src/main/java/com/opendesk/contact/suppression/SuppressionController.java
  modified: []
decisions:
  - "Used @OnDelete(action=OnDeleteAction.CASCADE) on GroupMembership FKs so Hibernate DDL generates real FK constraints with ON DELETE CASCADE — required because tests use ddl-auto=create-drop (Flyway disabled) and plain @Column UUIDs without @ManyToOne produce no FK at all"
  - "Idempotent suppress: check existsByUserIdAndPhoneE164 before insert rather than catching DataIntegrityViolationException to keep transaction clean"
  - "GroupMembership uses @IdClass composite PK with UUID fields as @Id; lazy @ManyToOne associations are insertable=false/updatable=false to avoid duplicate column mapping"
metrics:
  duration: "~25 minutes"
  completed: "2026-06-21"
  tasks_completed: 2
  files_changed: 21
---

# Phase 4 Plan 02: Contact Service Core Summary

Contact entity, groups with membership, and per-user suppression list — all with IDOR-guarded REST APIs backed by three Flyway migrations and full integration test coverage.

## What Was Built

### Task 1: Contact CRUD (CONT-01/02/03)
- `V1__create_contacts.sql`: `contacts` table with `uq_contact_user_phone UNIQUE(user_id, phone_e164)` and `idx_contacts_user_id` index.
- `Contact` JPA entity with `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate/@LastModifiedDate`, `userId` non-updatable.
- `ContactRepository`: `findByIdAndUserId` (IDOR), `findByUserId(Pageable)`, `existsByUserIdAndPhoneE164`.
- `ContactService`: create/update/delete/list; throws `ContactNotFoundException` (404) when `findByIdAndUserId` returns empty.
- `ContactController`: every handler derives `userId` from `auth.getToken().getSubject()` exclusively (T-04-01 mitigation).
- `SecurityConfig`: stateless JWT resource server, CSRF disabled, actuator/error permitted.
- `ContactCrudIT` GREEN: addContactPersistsAndIsRetrievable, editContactUpdatesFields, deleteContactRemovesFromGroups, IDOR returns 404.

### Task 2: Groups + Suppression (CONT-04, CONT-08)
- `V2__create_contact_groups.sql`: `contact_groups` with `uq_group_user_name UNIQUE(user_id,name)`; `contact_group_members` join table with `ON DELETE CASCADE` to both tables.
- `V3__create_suppressed_numbers.sql`: `suppressed_numbers` with `uq_suppressed_user_phone UNIQUE(user_id,phone_e164)`.
- `ContactGroup` entity, `GroupMembership` explicit join entity with `@OnDelete(CASCADE)` on both FK associations.
- `ContactGroupRepository.findContactPhonesByGroupIdsAndUserId` — native query returning distinct E.164 phones for a set of group IDs scoped to a user; **consumed by 04-05 campaign recipient expansion**.
- `GroupService`: createGroup, addMember (idempotent via check-before-insert), removeMember, listMembers.
- `GroupController`: `POST /api/v1/groups`, `PUT/DELETE /api/v1/groups/{groupId}/members/{contactId}`, `GET /api/v1/groups/{groupId}/members`.
- `SuppressedNumber` entity, `SuppressionRepository.existsByUserIdAndPhoneE164` — **key query consumed by 04-05 to exclude suppressed numbers from campaigns**.
- `SuppressionService.suppress` idempotent; `SuppressionController`: `POST /api/v1/suppression → 201`, `GET /api/v1/suppression → 200`.
- `ContactGroupIT` + `SuppressionIT` GREEN: membership persistence, cascade delete, per-user suppression scoping (D-08), idempotent suppress.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] GroupMembership FK cascade not firing under ddl-auto=create-drop**
- **Found during:** Task 2 (ContactGroupIT.groupMembershipIsPersisted line 109 asserting empty after contact delete)
- **Issue:** Plain `@Column UUID contactId/groupId` fields generate no FK constraint when Hibernate creates the schema. The `ON DELETE CASCADE` from V2 SQL migration is never applied in test runs (Flyway disabled). Deleting a Contact left orphaned GroupMembership rows.
- **Fix:** Added `@ManyToOne(insertable=false, updatable=false) @OnDelete(action=OnDeleteAction.CASCADE)` for both FK associations on `GroupMembership`. Hibernate now generates `REFERENCES ... ON DELETE CASCADE` when creating the table.
- **Files modified:** `GroupMembership.java`
- **Commit:** 008b743

## Suppression Query Surface for 04-05

`SuppressionRepository.existsByUserIdAndPhoneE164(UUID userId, String phoneE164)` is the contract consumed by plan 04-05 (campaign recipient expansion) to check whether a given phone number is suppressed before adding it to a dispatch batch. Per D-08: suppression is per-user global — the same number suppressed by user A does not suppress it for user B.

`ContactGroupRepository.findContactPhonesByGroupIdsAndUserId(Collection<UUID> groupIds, UUID userId)` returns distinct E.164 phone numbers for all contacts across a set of group IDs, scoped to a user. This is the fan-out query for group-targeted campaigns in 04-05.

## Known Stubs

None. All data flows are wired; no placeholder values.

## Threat Flags

None. All REST endpoints are JWT-authenticated. No new network surface beyond the planned contact/group/suppression APIs.

## TDD Gate Compliance

- RED gate (57cbd40): `test(04-02): RED — failing ITs for contact CRUD, groups, suppression` — committed by prior executor.
- GREEN gate Task 1 (9560702): `feat(04-02): contact CRUD API with IDOR guard (Task 1)`
- GREEN gate Task 2 (008b743): `feat(04-02): contact groups + suppression list (Task 2)`

## Self-Check: PASSED

- V1/V2/V3 migrations: present at `services/contact-service/src/main/resources/db/migration/`
- All 21 source files created and committed
- Commits 9560702, 008b743 exist in git log
- 7/7 ITs green: ContactCrudIT (4 tests), ContactGroupIT (1), SuppressionIT (1), plus compile checks
