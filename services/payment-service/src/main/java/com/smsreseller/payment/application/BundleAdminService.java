package com.smsreseller.payment.application;

import com.smsreseller.payment.application.port.BundleRepositoryPort;
import com.smsreseller.payment.domain.bundle.SmsBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service wrapping BundleRepository for admin create/update/delete operations (ADMN-07).
 *
 * <p>Create/update are transactional. Delete is idempotent for the same reason —
 * if the bundle is absent, throws {@link IllegalStateException} with "not found" for
 * controller to surface as 404.
 */
@Service
@RequiredArgsConstructor
public class BundleAdminService {

    private final BundleRepositoryPort bundleRepository;

    @Transactional
    public SmsBundle create(String name, int smsCount, long priceTzs, boolean active) {
        SmsBundle bundle = SmsBundle.builder()
                .id(UUID.randomUUID())
                .name(name)
                .smsCount(smsCount)
                .priceTzs(priceTzs)
                .isActive(active)
                .isPurchasable(true)
                .build();
        return bundleRepository.save(bundle);
    }

    @Transactional
    public SmsBundle update(UUID id, String name, int smsCount, long priceTzs, boolean active) {
        SmsBundle bundle = bundleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Bundle not found: " + id));
        bundle.setName(name);
        bundle.setSmsCount(smsCount);
        bundle.setPriceTzs(priceTzs);
        bundle.setActive(active);
        return bundleRepository.save(bundle);
    }

    @Transactional
    public void delete(UUID id) {
        if (!bundleRepository.existsById(id)) {
            throw new IllegalStateException("Bundle not found: " + id);
        }
        bundleRepository.deleteById(id);
    }
}
