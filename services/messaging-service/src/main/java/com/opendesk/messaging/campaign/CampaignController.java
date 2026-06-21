package com.opendesk.messaging.campaign;

import com.opendesk.messaging.message.DeliveryReceiptService;
import com.opendesk.messaging.message.MessageView;
import com.opendesk.messaging.wallet.InsufficientCreditsException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Campaign REST controller — campaign lifecycle endpoints.
 *
 * <p>IDOR guard: userId is always extracted from the JWT subject claim, never from the request body.
 * T-04-12: recipients expanded only for the authenticated user's groups.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final DeliveryReceiptService deliveryReceiptService;

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
     * Get a specific campaign with aggregate message counts (IDOR-safe, MESG-06).
     * Returns 404 if campaign is not owned by the requester.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> get(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return campaignService.findByIdAndUser(id, userId)
                .map(campaignService::toCampaignResponseWithCounts)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List per-message delivery statuses for a campaign (MESG-07).
     * IDOR-safe: returns 404 if campaign is not owned by the requester.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageView>> getMessages(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        // IDOR check in getMessages — returns empty list if not owned
        boolean owned = campaignService.findByIdAndUser(id, userId).isPresent();
        if (!owned) {
            return ResponseEntity.notFound().build();
        }
        List<MessageView> messages = campaignService.getMessages(id, userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * Dispatch a campaign for immediate send (MESG-03, MESG-08).
     *
     * <p>POST /api/v1/campaigns/{id}/send → 200 OK with CampaignDispatchResponse.
     * On insufficient credits → 402 Payment Required.
     * On campaign not found for this user → 404.
     *
     * <p>D-03: credit reservation is synchronous on the request path. The campaign transitions
     * to QUEUED only after a successful reservation. On InsufficientCreditsException, the
     * campaign remains in DRAFT (T-04-10).
     */
    @PostMapping("/{id}/send")
    public ResponseEntity<?> send(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());

        Campaign campaign = campaignService.findByIdAndUser(id, userId)
                .orElse(null);
        if (campaign == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            CampaignDispatchResponse dispatchResponse = campaignService.executeSend(campaign);
            return ResponseEntity.ok(dispatchResponse);
        } catch (InsufficientCreditsException e) {
            return ResponseEntity.status(402)
                    .body(Map.of("error", "insufficient_credits", "message", e.getMessage()));
        }
    }
}
