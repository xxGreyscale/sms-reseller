package com.opendesk.wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server security configuration for the wallet-service.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>CSRF: disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session</li>
 *   <li>Permit-all paths: actuator health + /error</li>
 *   <li>All other requests require authentication (anyRequest().authenticated())</li>
 *   <li>oauth2ResourceServer.jwt — validates tokens issued by identity-service using the
 *       shared RSA public key (configured in application.yml)</li>
 * </ul>
 *
 * <p>NO PasswordEncoder bean — wallet-service is a resource-server only; it never
 * handles user credentials or password-based authentication (ASVS V4).
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
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
