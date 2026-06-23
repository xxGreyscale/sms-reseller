package com.smsreseller.messaging.campaign;

import java.util.UUID;

/**
 * Response body for POST /api/v1/campaigns/{id}/send (MESG-08).
 *
 * <p>Reports the count of recipients queued and credits reserved so the caller can
 * display a confirmation screen without a separate GET.
 */
public record CampaignDispatchResponse(
        UUID campaignId,
        int recipientCount,
        int creditsReserved
) {}
