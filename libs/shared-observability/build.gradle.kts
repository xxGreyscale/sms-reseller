// libs/shared-observability/build.gradle.kts
// Source: docs.spring.io/spring-boot/3.5/appendix/dependency-versions [VERIFIED]
plugins {
    id("spring-boot-library")
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)           // BOM-managed: 1.15.12
    implementation(libs.micrometer.tracing.otel)        // BOM-managed: 1.5.12
    implementation(libs.otel.exporter.otlp)             // BOM-managed: 1.49.0
    implementation(libs.sentry.spring.boot)             // NOT in BOM — pinned to 8.44.0
    // CRITICAL: Do NOT use spring-boot-starter-opentelemetry — Boot 4 only artifact
    testImplementation(libs.spring.boot.starter.test)
}
