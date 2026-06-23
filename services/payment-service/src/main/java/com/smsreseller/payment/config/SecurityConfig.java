package com.smsreseller.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT resource-server security configuration for payment-service.
 *
 * <p>Contract:
 * <ul>
 *   <li>CSRF disabled — stateless JWT API (no session cookies)</li>
 *   <li>STATELESS session — no HttpSession created</li>
 *   <li>Actuator health + error + Azampay callback endpoint are public (no JWT required)</li>
 *   <li>/api/v1/admin/bundles/** requires ROLE_ADMIN (T-05-12)</li>
 *   <li>All other requests require a valid JWT Bearer token</li>
 *   <li>jwtAuthenticationConverter reads the {@code roles} claim from the JWT</li>
 * </ul>
 *
 * <p>Threat model: T-03-06 (unauthenticated access), T-05-12 (elevation of privilege via
 * user token on admin bundle endpoints).
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
                        // Azampay callback is public — signature-validated internally
                        .requestMatchers("/api/v1/payments/callback").permitAll()
                        // ADMN-07: admin-only bundle catalog mutations (T-05-12)
                        .requestMatchers("/api/v1/admin/bundles/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                return roles.stream()
                        .map(Object::toString)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
            return List.of();
        });
        return converter;
    }
}
