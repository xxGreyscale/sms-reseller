package com.opendesk.messaging.campaign;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.util.UUID;

/**
 * Campaign REST controller — campaign lifecycle endpoints.
 *
 * <p>IDOR guard: userId is always extracted from the JWT subject claim, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * Create a new campaign in DRAFT state (MESG-01).
     *
     * <p>POST /api/v1/campaigns → 201 Created with campaign in DRAFT state.
     * groupIds are stored; recipients are expanded at dispatch time (04-05).
     */
    @PostMapping
    public ResponseEntity<CampaignResponse> create(
            JwtAuthenticationToken auth,
            @Valid @RequestBody CreateCampaignRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        Campaign campaign = campaignService.create(userId, request);
        CampaignResponse response = CampaignResponse.from(campaign);
        return ResponseEntity
                .created(URI.create("/api/v1/campaigns/" + campaign.getId()))
                .body(response);
    }

    /**
     * List all campaigns for the authenticated user (MESG-06).
     */
    @GetMapping
    public Page<CampaignResponse> list(
            JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return campaignService.list(userId, PageRequest.of(page, size))
                .map(CampaignResponse::from);
    }

    /**
     * Get a specific campaign (IDOR-safe: returns 404 if not owned by the requester).
     */
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> get(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return campaignService.findByIdAndUser(id, userId)
                .map(CampaignResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
