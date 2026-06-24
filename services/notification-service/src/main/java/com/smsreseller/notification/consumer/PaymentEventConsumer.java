package com.smsreseller.notification.consumer;

import com.smsreseller.notification.idempotency.ProcessedEventRepository;
import com.smsreseller.notification.notification.NotificationService;
import com.smsreseller.notification.notification.NotificationType;
import com.smsreseller.notification.push.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Passive consumer for payment.events / payment.PaymentConfirmed — NOTF-02.
 *
 * <p>T-05-15: ignoreDeclarationExceptions="true" — passive bind on payment.events.
 * T-05-14: idempotency gate via processedEventRepository.tryInsert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final NotificationChannel notificationChannel;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "notification.payment.PaymentConfirmed", durable = "true"),
            exchange = @Exchange(name = "payment.events", type = "topic", durable = "true",
                    ignoreDeclarationExceptions = "true"),
            key = "payment.PaymentConfirmed"
    ))
    @Transactional
    public void onPaymentConfirmed(Events.PaymentConfirmedEvent event) {
        log.debug("Received PaymentConfirmed event: eventId={}, userId={}", event.eventId(), event.userId());

        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.info("Duplicate PaymentConfirmed event ignored: eventId={}", event.eventId());
            return;
        }

        String body = event.amountTzs() != null
                ? String.format("Your payment of TZS %,d has been confirmed. %d SMS credits added.",
                        event.amountTzs(), event.smsCredits() != null ? event.smsCredits() : 0)
                : "Your payment has been confirmed.";

        var notification = notificationService.create(
                UUID.fromString(event.userId()),
                NotificationType.PAYMENT_CONFIRMED,
                "Payment Confirmed",
                body,
                null
        );
        notificationChannel.push(notification);
        log.info("NOTF-02: PAYMENT_CONFIRMED notification created for userId={} (eventId={})",
                event.userId(), event.eventId());
    }
}
