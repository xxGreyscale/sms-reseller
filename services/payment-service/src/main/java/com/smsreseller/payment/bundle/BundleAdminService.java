package com.smsreseller.payment.bundle;

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

    private final BundleRepository bundleRepository;

    @Transactional
    public SmsBundle create(BundleSaveRequest req) {
        SmsBundle bundle = SmsBundle.builder()
                .id(UUID.randomUUID())
                .name(req.name())
                .smsCount(req.smsCount())
                .priceTzs(req.priceTzs())
                .isActive(req.active())
                .isPurchasable(true)
                .build();
        return bundleRepository.save(bundle);
    }

    @Transactional
    public SmsBundle update(UUID id, BundleSaveRequest req) {
        SmsBundle bundle = bundleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Bundle not found: " + id));
        bundle.setName(req.name());
        bundle.setSmsCount(req.smsCount());
        bundle.setPriceTzs(req.priceTzs());
        bundle.setActive(req.active());
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
