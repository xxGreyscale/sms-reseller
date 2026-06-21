package com.opendesk.messaging.config;

import com.opendesk.messaging.message.DeliveryReceiptService;
import com.opendesk.messaging.sms.StubSmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the {@link StubSmsProvider} delivery-receipt handler seam to
 * {@link DeliveryReceiptService#handleDeliveryReceipt}.
 *
 * <p>Only active under the {@code stub} profile — the seam is a no-op until this config runs.
 * This approach (ApplicationRunner) ensures both beans are fully initialized before the
 * handler is set, avoiding a circular-dependency risk from constructor injection.
 *
 * <p>D-12 / 04-04: {@code StubSmsProvider.setDeliveryReceiptHandler} is the designated seam;
 * plan 04-06 wires the real handler here without modifying the stub class.
 */
@Configuration
@Profile("stub")
@Slf4j
public class SmsProviderConfig {

    @Bean
    public ApplicationRunner wireStubDlrHandler(StubSmsProvider stubSmsProvider,
                                                 DeliveryReceiptService deliveryReceiptService) {
        return args -> {
            stubSmsProvider.setDeliveryReceiptHandler(
                    (externalId, status) -> deliveryReceiptService.handleDeliveryReceipt(externalId, status));
            log.info("StubSmsProvider DLR handler wired to DeliveryReceiptService");
        };
    }
}
