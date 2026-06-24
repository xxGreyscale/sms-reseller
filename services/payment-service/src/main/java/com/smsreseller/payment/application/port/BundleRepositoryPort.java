package com.smsreseller.payment.application.port;

import com.smsreseller.payment.domain.bundle.SmsBundle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: repository contract for {@link SmsBundle}.
 *
 * <p>Defined in the application layer (port) so that use-case services depend on the port,
 * not the infrastructure JPA implementation. Satisfies the inward dependency rule (ARCH-01).
 */
public interface BundleRepositoryPort {

    SmsBundle save(SmsBundle bundle);

    Optional<SmsBundle> findById(UUID id);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    List<SmsBundle> findAll();

    List<SmsBundle> findByIsActiveTrue();
}
