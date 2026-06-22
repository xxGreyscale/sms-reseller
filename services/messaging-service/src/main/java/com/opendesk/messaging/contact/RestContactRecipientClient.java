package com.opendesk.messaging.contact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link ContactRecipientClient} that calls the contact-service
 * internal group-recipients endpoint.
 *
 * <p>Uses {@code RestClient} (sync) per CLAUDE.md constraint (no RestTemplate).
 * No circuit breaker on this path — if contact-service is down, campaign dispatch fails
 * with a 503-style exception which propagates as a 500 to the caller. The campaign remains
 * in DRAFT status (no QUEUED transition) so the user can retry.
 *
 * <p>The contact-service endpoint {@code GET /api/v1/internal/contacts/recipients} applies
 * suppression filtering at the query layer (D-14, MESG-09). The messaging-service does not
 * need to apply suppression independently — it trusts the contact-service response.
 */
@Component
@Slf4j
public class RestContactRecipientClient implements ContactRecipientClient {

    private final RestClient restClient;

    public RestContactRecipientClient(
            @Value("${app.contact.base-url:http://contact-service}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public List<String> getRecipientsForGroups(Set<UUID> groupIds, UUID userId) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }

        String groupIdParams = groupIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String uri = UriComponentsBuilder
                .fromPath("/api/v1/internal/contacts/recipients")
                .queryParam("groupIds", groupIdParams)
                .queryParam("userId", userId.toString())
                .toUriString();

        log.debug("Fetching recipients for {} groups userId={}", groupIds.size(), userId);

        List<String> phones = restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});

        return phones != null ? phones : List.of();
    }

    @Override
    public List<String> getRecipientsByContactIds(Set<UUID> contactIds, UUID userId) {
        if (contactIds == null || contactIds.isEmpty()) {
            return List.of();
        }

        String contactIdParams = contactIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String uri = UriComponentsBuilder
                .fromPath("/api/v1/internal/contacts/recipients-by-ids")
                .queryParam("contactIds", contactIdParams)
                .queryParam("userId", userId.toString())
                .toUriString();

        log.debug("Fetching recipients for {} contactIds userId={}", contactIds.size(), userId);

        List<String> phones = restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});

        return phones != null ? phones : List.of();
    }
}
