package com.opendesk.messaging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server security configuration for messaging-service.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>CSRF disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session</li>
 *   <li>/api/v1/internal/** requires ROLE_ADMIN (T-04-05: sender-ID approval bypass prevention)</li>
 *   <li>Actuator health + /error permit-all</li>
 *   <li>All other requests require JWT authentication</li>
 * </ul>
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
                                "/error",
                                // DLR webhook — provider callbacks carry no JWT (T-04-16 accepted at MVP)
                                "/api/v1/messaging/dlr"
                        ).permitAll()
                        // T-04-05: Sender-ID approve/reject endpoints are ADMIN-only
                        .requestMatchers("/api/v1/internal/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
