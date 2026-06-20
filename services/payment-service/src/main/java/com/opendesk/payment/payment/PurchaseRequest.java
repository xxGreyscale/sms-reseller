package com.opendesk.payment.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for POST /api/v1/payments — initiates an Azampay STK push.
 *
 * @param bundleId the SMS bundle to purchase (must reference an active, purchasable bundle)
 * @param msisdn   customer phone number (E.164 format, e.g. "255712345678")
 * @param provider mobile money provider (e.g. "MPESA", "TIGOPESA", "AIRTELMONEY", "AZAMPESA")
 */
public record PurchaseRequest(
        @NotNull UUID bundleId,
        @NotBlank String msisdn,
        @NotBlank String provider
) {}
