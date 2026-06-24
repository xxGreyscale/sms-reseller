package com.smsreseller.payment.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user attempts to purchase a non-purchasable bundle (e.g. Taster / free bundle).
 *
 * <p>Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BundleNotPurchasableException extends RuntimeException {

    public BundleNotPurchasableException(String bundleId) {
        super("Bundle " + bundleId + " is not purchasable via Azampay.");
    }
}
