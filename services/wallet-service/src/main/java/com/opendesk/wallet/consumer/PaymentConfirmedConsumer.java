package com.opendesk.wallet.consumer;

import com.opendesk.wallet.lot.LotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code payment.PaymentConfirmed} events from the {@code payment.events} TopicExchange
 * and grants a PURCHASED credit lot (12-month expiry, D-03) to the user.
 *
 * <p>Idempotency: the {@code processed_events} table is checked atomically via
 * {@code INSERT ON CONFLICT DO NOTHING} before any lot grant. A duplicate delivery
 * of the same {@code eventId} is silently skipped — credit is granted exactly once (T-03-16).
 *
 * <p>Binding: wallet-service binds its own durable queue to the {@code payment.events}
 * exchange declared by payment-service. wallet-service does NOT redeclare {@code payment.events}
 * — the {@code @QueueBinding} handles passive binding.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final LotService lotService;

    @RabbitListener(bindings = @QueueBinding(
            value    = @Queue(name = "wallet.payment.PaymentConfirmed", durable = "true"),
            exchange = @Exchange(name = "payment.events", type = ExchangeTypes.TOPIC, durable = "true"),
            key      = "payment.PaymentConfirmed"
    ))
    @Transactional
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        // Idempotency guard: INSERT ON CONFLICT DO NOTHING — atomic, no SELECT-then-INSERT race
        if (!processedEventRepository.tryInsert(event.eventId())) {
            log.debug("PaymentConfirmedConsumer: duplicate eventId={} — skipping (T-03-16)",
                    event.eventId());
            return;
        }

        // Grant PURCHASED lot — 12-month expiry (D-03); grantPurchased computes expiresAt internally
        lotService.grantPurchased(event.userId(), event.smsCount(), event.paymentId());

        log.info("PaymentConfirmedConsumer: granted {} purchased credits to userId={} paymentId={}",
                event.smsCount(), event.userId(), event.paymentId());
    }
}
