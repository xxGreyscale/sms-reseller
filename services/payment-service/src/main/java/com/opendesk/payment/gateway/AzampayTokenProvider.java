package com.opendesk.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Fetches and caches an Azampay bearer token from the GenerateToken endpoint.
 *
 * <p>Token is refreshed when expired (sub-1-minute before expiry to account for clock skew).
 * Only active under {@code @Profile("prod")} — stub tests do not need real tokens.
 *
 * <p>Azampay token endpoint: POST /azampay/token/GenerateToken
 * Request: { appName, clientId, clientSecret }
 * Response: { accessToken, expire }
 */
@Profile("prod")
@Component
@Slf4j
public class AzampayTokenProvider {

    @Value("${app.azampay.base-url}")
    private String azampayBaseUrl;

    @Value("${app.azampay.app-name}")
    private String appName;

    @Value("${app.azampay.client-id}")
    private String clientId;

    @Value("${app.azampay.client-secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    private final RestClient restClient = RestClient.builder().build();

    /**
     * Returns a valid Azampay bearer token, refreshing if expired.
     *
     * @return bearer token string (without "Bearer " prefix)
     */
    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }
        return refreshToken();
    }

    @SuppressWarnings("unchecked")
    private String refreshToken() {
        log.info("AzampayTokenProvider: refreshing bearer token");
        Map<String, String> requestBody = Map.of(
                "appName", appName,
                "clientId", clientId,
                "clientSecret", clientSecret
        );

        Map<String, Object> response = restClient.post()
                .uri(azampayBaseUrl + "/azampay/token/GenerateToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("accessToken")) {
            throw new RuntimeException("AzampayTokenProvider: invalid token response");
        }

        this.cachedToken = (String) response.get("accessToken");
        // expire field is a timestamp string — parse as ISO or use 55-minute fallback
        String expireStr = (String) response.getOrDefault("expire", null);
        this.tokenExpiry = expireStr != null
                ? Instant.parse(expireStr)
                : Instant.now().plusSeconds(55 * 60); // 55-minute fallback

        log.info("AzampayTokenProvider: token refreshed, expires at {}", tokenExpiry);
        return cachedToken;
    }
}
