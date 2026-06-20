package com.opendesk.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource-server security configuration for payment-service (T-03-06).
 *
 * <p>Contract:
 * <ul>
 *   <li>CSRF disabled — stateless JWT API (no session cookies)</li>
 *   <li>STATELESS session — no HttpSession created</li>
 *   <li>Actuator health + error + Azampay callback endpoint are public (no JWT required)</li>
 *   <li>All other requests require a valid JWT Bearer token (signed by identity-service)</li>
 *   <li>JWT decoder is provided by {@code shared-security} {@code JwtConfig} bean</li>
 * </ul>
 *
 * <p>No {@code PasswordEncoder}, no {@code DaoAuthenticationProvider}, no {@code UserDetailsService}
 * — payment-service is a pure JWT resource server (identity-service issues tokens, not here).
 *
 * <p>Threat model: T-03-06 — elevation of privilege via unauthenticated bundle/payment access.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/error"
                        ).permitAll()
                        // Azampay callback is public — signature-validated internally (Plan 05)
                        .requestMatchers("/api/v1/payments/callback").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
