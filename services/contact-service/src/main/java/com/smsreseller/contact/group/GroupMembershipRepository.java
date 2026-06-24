package com.smsreseller.contact.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, GroupMembership.MembershipId> {

    List<GroupMembership> findByGroupId(UUID groupId);

    Optional<GroupMembership> findByGroupIdAndContactId(UUID groupId, UUID contactId);

    void deleteByGroupIdAndContactId(UUID groupId, UUID contactId);
}
