package com.smsreseller.contact.group;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for named contact groups (CONT-04).
 *
 * <p>IDOR guard (T-04-01): every handler derives userId exclusively from
 * {@code auth.getToken().getSubject()} — never from request body or path variables.
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * Create a new group.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto create(
            JwtAuthenticationToken auth,
            @Valid @RequestBody CreateGroupRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return GroupDto.from(groupService.createGroup(userId, request.name()));
    }

    /**
     * Add a contact to a group. Idempotent.
     */
    @PutMapping("/{groupId}/members/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(
            JwtAuthenticationToken auth,
            @PathVariable UUID groupId,
            @PathVariable UUID contactId) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        groupService.addMember(userId, groupId, contactId);
    }

    /**
     * Remove a contact from a group.
     */
    @DeleteMapping("/{groupId}/members/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            JwtAuthenticationToken auth,
            @PathVariable UUID groupId,
            @PathVariable UUID contactId) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        groupService.removeMember(userId, groupId, contactId);
    }

    /**
     * List all members (contact IDs) of a group.
     */
    @GetMapping("/{groupId}/members")
    public List<MemberDto> listMembers(
            JwtAuthenticationToken auth,
            @PathVariable UUID groupId) {
        UUID userId = UUID.fromString(auth.getToken().getSubject()); // IDOR guard
        return groupService.listMembers(userId, groupId)
                .stream()
                .map(m -> new MemberDto(m.getContactId()))
                .toList();
    }

    // ── Inner request/response records ───────────────────────────────────────

    public record CreateGroupRequest(
            @NotBlank(message = "name is required") String name
    ) {}

    public record GroupDto(UUID id, UUID userId, String name) {
        static GroupDto from(ContactGroup g) {
            return new GroupDto(g.getId(), g.getUserId(), g.getName());
        }
    }

    public record MemberDto(UUID contactId) {}
}
