package com.opendesk.contact.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server security configuration for contact-service.
 *
 * <p>Security decisions (mirrors wallet-service SecurityConfig pattern):
 * <ul>
 *   <li>CSRF: disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session</li>
 *   <li>Permit-all paths: actuator health + /error</li>
 *   <li>All other requests require authentication</li>
 *   <li>oauth2ResourceServer.jwt — validates tokens issued by identity-service</li>
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
                                // Internal service-to-service endpoints (messaging-service →
                                // contact-service). Not exposed externally — Traefik network
                                // policy restricts at the infrastructure layer.
                                "/api/v1/internal/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
