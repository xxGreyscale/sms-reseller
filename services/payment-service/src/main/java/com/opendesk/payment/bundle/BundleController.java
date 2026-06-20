package com.opendesk.payment.bundle;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only bundle catalog API (PYMT-01).
 *
 * <p>GET /api/v1/bundles — returns all active SMS bundles for authenticated users.
 * Authentication is enforced by {@code SecurityConfig} (JWT Bearer token required).
 *
 * <p>Write operations (create/update/deactivate) are Phase 5 admin UI — not here.
 */
@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
public class BundleController {

    private final BundleRepository bundleRepository;

    /**
     * Returns all active SMS bundles as DTOs.
     *
     * <p>Bundles are seeded by V2__seed_sms_bundles.sql (Flyway). The Taster bundle
     * is included but marked {@code isPurchasable=false} — it is only granted on
     * NIDA verification, not purchasable via Azampay.
     *
     * @return list of active bundles ordered by DB insertion (Taster first by seed order)
     */
    @GetMapping
    public List<BundleDto> listBundles() {
        return bundleRepository.findByIsActiveTrue()
                .stream()
                .map(BundleDto::from)
                .toList();
    }
}
