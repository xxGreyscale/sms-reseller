package com.smsreseller.contact.contact;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a contact is not found or belongs to another user (IDOR 404 pattern).
 *
 * <p>Returns 404 (not 403) to avoid leaking information about resource existence (T-04-01).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException(String message) {
        super(message);
    }
}
