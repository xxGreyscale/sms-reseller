package com.smsreseller.notification.push;

import com.smsreseller.notification.notification.Notification;

/**
 * Push channel seam (D-04).
 *
 * <p>Implementations route a saved {@link Notification} to an external push provider.
 * The only active implementation at MVP is {@link StubPushChannel} (active under the
 * {@code stub} profile). Real FCM integration is deferred to Phase 6.
 */
public interface NotificationChannel {

    /**
     * Deliver a push notification for the given in-app notification row.
     *
     * @param notification the persisted notification to push
     */
    void push(Notification notification);
}
