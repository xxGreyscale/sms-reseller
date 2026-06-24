/**
 * Shared security library for the sms-reseller platform.
 *
 * <p>Provides JWT validation utilities consumed by all 8 service modules.
 * The JWT resource server configuration is wired via Spring Security 6.5
 * using {@code NimbusJwtDecoder} builders.
 */
package com.smsreseller.shared.security;
