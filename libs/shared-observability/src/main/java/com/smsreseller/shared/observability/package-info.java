/**
 * Shared observability library for the sms-reseller platform.
 *
 * <p>Wires OpenTelemetry traces, Prometheus metrics, and Sentry error tracking
 * via Spring Boot 3.5 BOM-managed dependencies. All 8 service modules depend on
 * this library. Uses the manual OTel approach (micrometer-tracing-bridge-otel +
 * opentelemetry-exporter-otlp) — NOT the spring-boot-starter-opentelemetry
 * artifact which is Boot 4 only.
 */
package com.smsreseller.shared.observability;
