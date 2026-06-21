package com.opendesk.messaging.senderid;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * User-facing sender-ID request endpoints (SNDR-02).
 *
 * <p>IDOR guard: userId always comes from JWT subject, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/sender-ids")
@RequiredArgsConstructor
public class SenderIdController {

    private final SenderIdService senderIdService;

    /**
     * Submit a custom sender-ID request (SNDR-02).
     * POST /api/v1/sender-ids/requests → 201 Created.
     * senderName validation: max 11 chars, alphanumeric only.
     */
    @PostMapping("/requests")
    public ResponseEntity<SenderIdDto.SenderIdResponse> submit(
            JwtAuthenticationToken auth,
            @Valid @RequestBody SenderIdDto.SubmitRequest body) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        SenderIdRequest req = senderIdService.request(userId, body.senderName());
        SenderIdDto.SenderIdResponse response = SenderIdDto.SenderIdResponse.from(req);
        return ResponseEntity
                .created(URI.create("/api/v1/sender-ids/requests/" + req.getId()))
                .body(response);
    }

    /**
     * List own sender-ID requests.
     * GET /api/v1/sender-ids/requests → 200 OK.
     */
    @GetMapping("/requests")
    public ResponseEntity<List<SenderIdDto.SenderIdResponse>> list(
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        List<SenderIdDto.SenderIdResponse> responses = senderIdService.listForUser(userId)
                .stream()
                .map(SenderIdDto.SenderIdResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
