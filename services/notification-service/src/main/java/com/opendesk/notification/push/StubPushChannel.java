package com.opendesk.notification.push;

import com.opendesk.notification.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * No-op stub push channel active under the {@code stub} Spring profile.
 *
 * <p>Records all pushes in an in-memory list so integration tests can verify calls
 * without hitting a real FCM endpoint. Real FCM implementation is deferred to Phase 6.
 *
 * <p>Active in local dev (profile {@code dev → stub} group in application.yml) and
 * integration tests (profile {@code stub} in AbstractNotificationIntegrationTest).
 */
@Profile("stub")
@Component
@Slf4j
public class StubPushChannel implements NotificationChannel {

    private final List<Notification> recorded = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void push(Notification notification) {
        recorded.add(notification);
        log.debug("StubPushChannel: recorded push type={} userId={}", notification.getType(), notification.getUserId());
    }

    /** Returns an unmodifiable snapshot of recorded pushes — for test assertions. */
    public List<Notification> getRecorded() {
        return Collections.unmodifiableList(new ArrayList<>(recorded));
    }

    /** Clears recorded pushes — call in @BeforeEach if needed across multiple tests. */
    public void clear() {
        recorded.clear();
    }
}
