package com.opendesk.wallet.config;

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
 * Stateless JWT resource-server security configuration for the wallet-service.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>CSRF: disabled — stateless API, no browser sessions (CLAUDE.md)</li>
 *   <li>Session management: STATELESS — JWT is the session</li>
 *   <li>Permit-all paths: actuator health + /error</li>
 *   <li>/api/v1/admin/** — requires ROLE_ADMIN (ADMN-03, ADMN-05)</li>
 *   <li>/api/v1/analytics/** — requires any authenticated user (ANLX-02)</li>
 *   <li>All other requests require authentication (anyRequest().authenticated())</li>
 *   <li>oauth2ResourceServer.jwt — validates tokens issued by identity-service using the
 *       shared RSA public key (configured in application.yml)</li>
 *   <li>jwtAuthenticationConverter reads {@code roles} claim (List&lt;String&gt;) and maps
 *       each entry as a {@link SimpleGrantedAuthority} — required for hasRole("ADMIN") to work
 *       when roles are stored as "ROLE_ADMIN" strings in the JWT (05-PATTERNS.md §SecurityConfig)</li>
 * </ul>
 *
 * <p>NO PasswordEncoder bean — wallet-service is a resource-server only; it never
 * handles user credentials or password-based authentication (ASVS V4).
 *
 * <p>Threat mitigations:
 * <ul>
 *   <li>T-05-09: Elevation of privilege — /api/v1/admin/** guarded with hasRole("ADMIN")</li>
 *   <li>T-05-10: IDOR in analytics — controller derives userId from JWT subject (not query param)</li>
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
                                "/error"
                        ).permitAll()
                        // ADMN-03 + ADMN-05: admin ledger inspection and manual refund access
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // ANLX-02: credit usage analytics — any authenticated user (owner-scoped in controller)
                        .requestMatchers("/api/v1/analytics/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Reads the {@code roles} claim from the JWT and maps each string to a
     * {@link SimpleGrantedAuthority}. This makes hasRole("ADMIN") work when
     * the claim contains "ROLE_ADMIN".
     */
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
