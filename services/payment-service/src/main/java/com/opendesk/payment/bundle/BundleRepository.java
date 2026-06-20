package com.opendesk.payment.bundle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SmsBundle} — read-mostly (catalog is seeded via Flyway).
 *
 * <p>Admin UI (Phase 5) will add write operations (create/update/deactivate bundles).
 */
public interface BundleRepository extends JpaRepository<SmsBundle, UUID> {

    /**
     * Returns all active bundles for display in the catalog API.
     * Inactive bundles are hidden from customers but preserved in DB for history.
     */
    List<SmsBundle> findByIsActiveTrue();
}
