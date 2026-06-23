package com.smsreseller.identity.token;

/**
 * Thrown when a refresh token is invalid, expired, or has already been rotated.
 *
 * <p>Callers (SessionController) map this to HTTP 401 Unauthorized.
 * The message is intentionally generic — do not leak rotation state to clients (T-02-05).
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
