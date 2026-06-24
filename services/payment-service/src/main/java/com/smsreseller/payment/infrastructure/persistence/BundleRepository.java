package com.smsreseller.payment.infrastructure.persistence;

import com.smsreseller.payment.application.port.BundleRepositoryPort;
import com.smsreseller.payment.domain.bundle.SmsBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of the {@link BundleRepositoryPort} output port.
 */
public interface BundleRepository extends JpaRepository<SmsBundle, UUID>, BundleRepositoryPort {

    @Override
    SmsBundle save(SmsBundle bundle);

    List<SmsBundle> findByIsActiveTrue();
}
