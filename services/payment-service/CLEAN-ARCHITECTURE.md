# Clean Architecture Reference — payment-service

This document records the package layout, dependency rules, and rollout playbook adopted in
Phase 07 (ARCH-01). It is the authoritative reference for any future work touching this service.

---

## Package Layout

```
com.smsreseller.payment
├── domain/                          ← Enterprise business rules (innermost ring)
│   ├── payment/
│   │   ├── Payment.java             ← JPA entity (pragmatic: no domain/persistence split at MVP)
│   │   ├── PaymentStatus.java       ← Enum: PENDING | SUCCESS | FAILED | EXPIRED
│   │   ├── BundleNotPurchasableException.java
│   │   └── PendingPaymentExistsException.java
│   ├── bundle/
│   │   └── SmsBundle.java           ← JPA entity
│   └── outbox/
│       ├── OutboxEntry.java         ← JPA entity
│       └── PaymentConfirmedEvent.java  ← Payload record for outbox relay
│
├── application/                     ← Use-case services + output port contracts
│   ├── PaymentService.java          ← PYMT-02, PYMT-05, single-pending enforcement
│   ├── BundleAdminService.java      ← ADMN-07 create/update/delete bundles
│   ├── CallbackProcessor.java       ← PYMT-04/06 idempotent callback handling
│   └── port/                        ← Output port interfaces (dependency inversion)
│       ├── PaymentRepositoryPort.java
│       ├── BundleRepositoryPort.java
│       ├── OutboxRepositoryPort.java
│       ├── PaymentGateway.java      ← STK push + transaction status
│       ├── WebhookSignatureValidator.java
│       ├── AzampayCallbackPayload.java  ← lives here so port does not import infrastructure
│       ├── StkPushRequest.java
│       ├── StkPushResult.java
│       └── TransactionStatusResult.java
│
├── infrastructure/                  ← Framework adapters (JPA, RabbitMQ, Azampay, config)
│   ├── persistence/
│   │   ├── PaymentRepository.java   ← extends JpaRepository<Payment,UUID> + PaymentRepositoryPort
│   │   ├── BundleRepository.java    ← extends JpaRepository<SmsBundle,UUID> + BundleRepositoryPort
│   │   └── OutboxRepository.java    ← extends JpaRepository<OutboxEntry,UUID> + OutboxRepositoryPort
│   ├── gateway/
│   │   ├── AzampayPaymentGateway.java   ← implements PaymentGateway
│   │   ├── StubPaymentGateway.java      ← implements PaymentGateway (dev/test)
│   │   ├── AzampayTokenProvider.java
│   │   └── AzampayTransientException.java
│   ├── callback/
│   │   └── StubSignatureValidator.java  ← implements WebhookSignatureValidator
│   ├── messaging/
│   │   └── OutboxRelay.java         ← polls OutboxRepository, publishes to RabbitMQ
│   ├── scheduling/
│   │   ├── ReconciliationJob.java   ← polls Azampay transaction status for PENDING payments
│   │   └── PaymentTimeoutJob.java   ← expires stale PENDING payments
│   └── config/
│       ├── RabbitMqConfig.java
│       ├── Resilience4jConfig.java
│       └── SecurityConfig.java
│
└── presentation/                    ← REST controllers + request/response DTOs
    ├── PaymentController.java       ← POST /payments, GET /payments, GET /payments/{id}
    ├── PaymentDto.java
    ├── PurchaseRequest.java
    ├── BundleController.java        ← GET /bundles
    ├── BundleDto.java
    ├── AdminBundleController.java   ← POST/PUT/DELETE /admin/bundles
    ├── BundleSaveRequest.java
    └── CallbackController.java      ← POST /callbacks/azampay
```

---

## Dependency Rules (ARCH-01)

Enforced by `PaymentArchitectureTest` using ArchUnit `layeredArchitecture().consideringOnlyDependenciesInLayers()`.

```
Domain      ← may be accessed by: Application, Infrastructure, Presentation
Application ← may be accessed by: Infrastructure, Presentation
             (application services may NOT import infrastructure — port inversion required)
Infrastructure ← may be accessed by: Presentation only
Presentation ← may not be accessed by any layer
```

Key constraint: **Application must never import Infrastructure**.
Use case services (`PaymentService`, `BundleAdminService`, `CallbackProcessor`) receive
repository ports via constructor injection. Spring injects the JPA implementations at runtime.

---

## Port + Adapter Pattern for Repositories

The `application/port` package hosts three output port interfaces:

| Port | Infrastructure Adapter |
|------|------------------------|
| `PaymentRepositoryPort` | `infrastructure.persistence.PaymentRepository` |
| `BundleRepositoryPort`  | `infrastructure.persistence.BundleRepository` |
| `OutboxRepositoryPort`  | `infrastructure.persistence.OutboxRepository` |

Each infrastructure JPA interface extends **both** `JpaRepository<T, ID>` and the corresponding
port. This dual-extension pattern satisfies Spring Data (which needs `JpaRepository` for proxy
generation) while satisfying the dependency inversion principle (application binds to the port).

**Ambiguity resolution**: `JpaRepository.save` uses a generic return type `<S extends T> S save(S entity)`.
When the port defines `T save(T entity)`, the two signatures become ambiguous. The JPA repo
explicitly `@Override`s `save` and `findById` with concrete types to resolve this.

```java
// infrastructure/persistence/PaymentRepository.java
public interface PaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepositoryPort {
    @Override
    Payment save(Payment payment);   // resolves JPA generic vs port concrete ambiguity

    @Override
    Optional<Payment> findById(UUID id);  // same reason
    // ... custom query methods ...
}
```

---

## AzampayCallbackPayload placement

`AzampayCallbackPayload` lives in `application/port` (not `infrastructure/callback`).

Rationale: `WebhookSignatureValidator` (a port interface in `application/port`) references
`AzampayCallbackPayload` in its method signature. If `AzampayCallbackPayload` were in
`infrastructure`, the port interface would import infrastructure — violating the inward rule.
Moving the payload record to `application/port` keeps both the port and its parameter types
within the same layer.

---

## Method Signature Convention for Application Services

Application services must not import `presentation` layer classes. DTOs like `PurchaseRequest`
and `BundleSaveRequest` live in `presentation`; controllers unpack them before calling services.

```java
// Correct — application takes primitives/domain types, not presentation DTOs
paymentService.initiate(userId, request.bundleId(), request.msisdn(), request.provider());

// Wrong — would create presentation → application → presentation circular dependency
paymentService.initiate(userId, request);   // request is PurchaseRequest from presentation
```

---

## Pragmatic Compromises (MVP)

| Decision | Reason | Future path |
|----------|--------|-------------|
| JPA entities serve as domain model | Avoiding a separate persistence model reduces boilerplate at MVP scale; one service, low churn | Introduce domain objects + JPA entity mapping when the domain evolves independently |
| `BundleController` injects `infrastructure.persistence.BundleRepository` | Presentation → Infrastructure is allowed by the layered rule; controller delegates directly to the read repo for simplicity | If query complexity grows, add a `BundleQueryService` in the application layer |
| `deleteAll()` on port interfaces | Test-only utility (needed by `@BeforeEach` cleanup); included in port so tests can inject port, not infra | Separate test-only helpers into a `TestPaymentRepository` that extends the port |

---

## ArchUnit Test

`PaymentArchitectureTest` in `src/test/java` validates two rules:

1. **Layered architecture rule** — `consideringOnlyDependenciesInLayers()` checks that
   inter-layer dependencies flow inward only (no Application → Infrastructure).

2. **DoNotInclude rule** — Verifies that test classes annotated with `@SpringBootTest` are
   not considered when evaluating production architecture constraints.

Run via:
```bash
./gradlew :services:payment-service:test
```

Both rules must remain GREEN before any future package restructuring is merged.

---

## Rollout Playbook — Applying This Pattern to Other Services

When re-layering another service (e.g., `identity-service`, `messaging-service`):

1. **Map classes to layers** using the research file (`07-RESEARCH.md` pattern):
   - Entities, enums, domain exceptions → `domain/`
   - Use-case services, port interfaces, port DTOs → `application/` + `application/port/`
   - JPA repos, HTTP clients, AMQP producers, scheduled jobs, Spring config → `infrastructure/`
   - REST controllers, request/response DTOs → `presentation/`

2. **Identify cross-layer imports** that violate the inward rule. Any `application.*` importing
   `infrastructure.*` must be replaced with a port interface.

3. **Write the ArchUnit test first** (RED) using the same `layeredArchitecture()` pattern —
   see `PaymentArchitectureTest` for the exact configuration.

4. **Move classes** using IDE rename + find/replace for package declarations and imports.

5. **Update test imports** — integration tests in feature sub-packages may rely on
   same-package resolution. Add explicit imports for any class moved to a different package.

6. **Resolve JPA generic ambiguity** in dual-extension infra repos (see above).

7. **Run full test suite** — behavior-neutral constraint means all pre-existing tests must pass.

8. **Commit and update `CLEAN-ARCHITECTURE.md`** for the target service.
