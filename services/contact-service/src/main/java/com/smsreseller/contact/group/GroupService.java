package com.smsreseller.contact.group;

import com.smsreseller.contact.contact.ContactNotFoundException;
import com.smsreseller.contact.contact.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for contact groups (CONT-04).
 * All operations are scoped to the authenticated user (IDOR guard).
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final ContactGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final ContactRepository contactRepository;

    @Transactional
    public ContactGroup createGroup(UUID userId, String name) {
        ContactGroup group = ContactGroup.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .build();
        return groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public ContactGroup getGroup(UUID userId, UUID groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupNotFoundException(groupId.toString()));
    }

    /**
     * Add a contact to a group. Both group and contact must belong to the same user.
     */
    @Transactional
    public void addMember(UUID userId, UUID groupId, UUID contactId) {
        // Verify group ownership (IDOR guard)
        groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupNotFoundException(groupId.toString()));
        // Verify contact ownership (IDOR guard)
        contactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new ContactNotFoundException(contactId.toString()));

        // Idempotent: if membership already exists, do nothing
        if (membershipRepository.findByGroupIdAndContactId(groupId, contactId).isEmpty()) {
            membershipRepository.save(GroupMembership.builder()
                    .groupId(groupId)
                    .contactId(contactId)
                    .build());
        }
    }

    /**
     * Remove a contact from a group.
     */
    @Transactional
    public void removeMember(UUID userId, UUID groupId, UUID contactId) {
        groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupNotFoundException(groupId.toString()));
        membershipRepository.deleteByGroupIdAndContactId(groupId, contactId);
    }

    /**
     * List all membership records for a group, scoped to userId.
     */
    @Transactional(readOnly = true)
    public List<GroupMembership> listMembers(UUID userId, UUID groupId) {
        groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new GroupNotFoundException(groupId.toString()));
        return membershipRepository.findByGroupId(groupId);
    }
}
