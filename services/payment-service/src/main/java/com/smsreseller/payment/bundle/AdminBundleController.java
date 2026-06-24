package com.smsreseller.payment.bundle;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ADMIN-guarded CRUD over the SMS bundle catalog (ADMN-07).
 *
 * <p>All paths under /api/v1/admin/** are protected by SecurityConfig → hasRole("ADMIN").
 * ROLE_USER tokens receive 403; unauthenticated requests receive 401.
 *
 * <p>Threat model:
 * <ul>
 *   <li>T-05-12: elevation of privilege — SecurityConfig enforces ROLE_ADMIN</li>
 *   <li>T-05-13: tampering via non-positive pricing — @Positive/@Min(1) on BundleSaveRequest</li>
 * </ul>
 *
 * <p>The customer-facing read catalog (GET /api/v1/bundles, PYMT-01) is unaffected —
 * it lives in {@link BundleController} and remains accessible to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/admin/bundles")
@RequiredArgsConstructor
public class AdminBundleController {

    private final BundleRepository bundleRepository;
    private final BundleAdminService bundleAdminService;

    /**
     * Lists all bundles (active and inactive) for admin management.
     */
    @GetMapping
    public List<BundleDto> list() {
        return bundleRepository.findAll().stream().map(BundleDto::from).toList();
    }

    /**
     * Creates a new SMS bundle. Returns 201 Created with the saved bundle.
     */
    @PostMapping
    public ResponseEntity<BundleDto> create(@Valid @RequestBody BundleSaveRequest req) {
        SmsBundle saved = bundleAdminService.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/bundles/" + saved.getId()))
                .body(BundleDto.from(saved));
    }

    /**
     * Updates an existing bundle by ID. Returns 404 if the bundle does not exist.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BundleDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody BundleSaveRequest req) {
        try {
            SmsBundle updated = bundleAdminService.update(id, req);
            return ResponseEntity.ok(BundleDto.from(updated));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(409).body(null);
        }
    }

    /**
     * Deletes a bundle by ID. Returns 204 No Content on success, 404 if absent.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            bundleAdminService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(409).build();
        }
    }
}
